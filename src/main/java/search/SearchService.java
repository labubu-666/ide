package search;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain-text substring search across all readable files under a root directory.
 * Skips known build/VCS folders and files larger than {@link #MAX_FILE_SIZE_BYTES}.
 */
public class SearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".atlassian-cache", ".git", ".idea", ".gradle", ".pytest_cache", ".venv", ".vscode", ".settings",
            ".ide", "build", "target", "out", "bin", "dist", "node_modules");

    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    public static final int DEFAULT_MAX_RESULTS = 500;
    private static final int INDEX_VERSION = 1;
    private static final int INDEX_MAGIC = 0x53494458;
    private static final int METADATA_MAGIC = 0x53494D45;
    private static final int GRAM_SIZE = 3;

    private static final Path INDEX_DIR = Path.of(".ide", "search-index");
    private static final String INDEX_FILE_NAME = "search.idx";
    private static final String METADATA_FILE_NAME = "metadata.bin";

    private static final Map<Path, SearchIndexData> INDEX_CACHE = new ConcurrentHashMap<>();

    private final Path rootPath;

    public SearchService(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
    }

    public static void initializeIndex(Path rootPath) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        long startedAt = System.nanoTime();
        try {
            SearchSnapshot snapshot = collectSnapshot(normalizedRoot);
            Path indexDir = normalizedRoot.resolve(INDEX_DIR);
            Path metadataPath = indexDir.resolve(METADATA_FILE_NAME);
            Path indexPath = indexDir.resolve(INDEX_FILE_NAME);

            boolean metadataCurrent = isMetadataCurrent(metadataPath, snapshot.signatureHash(), snapshot.fileCount());
            if (metadataCurrent && Files.exists(indexPath)) {
                SearchIndexData loaded = loadIndex(normalizedRoot, indexPath);
                INDEX_CACHE.put(normalizedRoot, loaded);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                LOGGER.info("[SearchService] Index loaded in {}ms (files={}, lines={})", elapsedMs, snapshot.fileCount(), loaded.lines().size());
                return;
            }

            SearchIndexData rebuilt = buildIndex(normalizedRoot, snapshot.files());
            Files.createDirectories(indexDir);
            writeIndex(normalizedRoot, indexPath, rebuilt.lines());
            writeMetadata(metadataPath, snapshot.signatureHash(), snapshot.fileCount());
            INDEX_CACHE.put(normalizedRoot, rebuilt);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.info("[SearchService] Index rebuilt in {}ms (files={}, lines={})", elapsedMs, snapshot.fileCount(), rebuilt.lines().size());
        } catch (Exception ex) {
            INDEX_CACHE.remove(normalizedRoot);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.warn("[SearchService] Index initialization failed after {}ms, falling back to scan search: {}", elapsedMs, ex.getMessage());
        }
    }

    static void clearIndexCacheForTests() {
        INDEX_CACHE.clear();
    }

    public List<SearchMatch> search(String query) throws IOException {
        return search(query, DEFAULT_MAX_RESULTS);
    }

    public List<SearchMatch> search(String query, int maxResults) throws IOException {
        if (query == null || query.isEmpty()) return List.of();
        long startedAt = System.nanoTime();

        SearchIndexData index = INDEX_CACHE.get(rootPath);
        if (index != null) {
            try {
                List<SearchMatch> results = searchIndexed(query, maxResults, index);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                LOGGER.info("[SearchService] search path=indexed query='{}' results={} elapsedMs={}", summarizeQuery(query), results.size(), elapsedMs);
                return results;
            } catch (Exception ex) {
                INDEX_CACHE.remove(rootPath);
                LOGGER.warn("[SearchService] Indexed query failed, using scan fallback: {}", ex.getMessage());
            }
        }

        List<SearchMatch> results = searchParallel(query, maxResults);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        LOGGER.info("[SearchService] search path=parallel-scan query='{}' results={} elapsedMs={}", summarizeQuery(query), results.size(), elapsedMs);
        return results;
    }

    private List<SearchMatch> searchParallel(String query, int maxResults) throws IOException {
        List<Path> files = collectSearchableFiles(rootPath);
        if (files.isEmpty()) return List.of();

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<SearchMatch> queue = new ConcurrentLinkedQueue<>();
        AtomicInteger count = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(files.size());

        try {
            for (Path file : files) {
                futures.add(pool.submit(() -> searchFileParallel(file, query, maxResults, queue, count)));
            }

            for (Future<?> future : futures) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Search interrupted");
                }
                future.get();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            throw new IOException("Parallel search failed", ex.getCause());
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<SearchMatch> results = new ArrayList<>(queue);
        results.sort(Comparator
                .comparing((SearchMatch m) -> m.filePath().toString())
                .thenComparingInt(SearchMatch::lineNumber)
                .thenComparingInt(SearchMatch::columnStart));
        if (results.size() > maxResults) {
            return new ArrayList<>(results.subList(0, maxResults));
        }
        return results;
    }

    private void searchFileParallel(Path file, String query, int maxResults,
                                    ConcurrentLinkedQueue<SearchMatch> queue,
                                    AtomicInteger count) {
        if (Thread.currentThread().isInterrupted()) return;
        if (count.get() >= maxResults) return;

        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) return;
                if (count.get() >= maxResults) return;

                lineNum++;
                int idx = line.indexOf(query);
                while (idx >= 0) {
                    int newCount = count.incrementAndGet();
                    if (newCount > maxResults) return;
                    queue.add(new SearchMatch(file, lineNum, line, idx, idx + query.length()));
                    idx = line.indexOf(query, idx + query.length());
                }
            }
        } catch (MalformedInputException e) {
            // binary or non-UTF-8 file — skip
        } catch (IOException e) {
            // unreadable — skip
        }
    }

    private List<SearchMatch> searchIndexed(String query, int maxResults, SearchIndexData index) {
        if (query.length() < GRAM_SIZE) {
            return searchIndexedLinearly(query, maxResults, index.lines());
        }

        List<String> grams = gramsForQuery(query);
        if (grams.isEmpty()) return List.of();

        int[] base = null;
        List<int[]> postings = new ArrayList<>(grams.size());
        for (String gram : grams) {
            int[] ids = index.gramIndex().get(gram);
            if (ids == null || ids.length == 0) return List.of();
            postings.add(ids);
            if (base == null || ids.length < base.length) {
                base = ids;
            }
        }

        List<SearchMatch> results = new ArrayList<>();
        for (int lineId : base) {
            if (results.size() >= maxResults) break;
            if (!presentInAll(lineId, postings)) continue;

            IndexedLine line = index.lines().get(lineId);
            int idx = line.lineText().indexOf(query);
            while (idx >= 0) {
                results.add(new SearchMatch(line.filePath(), line.lineNumber(), line.lineText(), idx, idx + query.length()));
                if (results.size() >= maxResults) return results;
                idx = line.lineText().indexOf(query, idx + query.length());
            }
        }
        return results;
    }

    private List<SearchMatch> searchIndexedLinearly(String query, int maxResults, List<IndexedLine> lines) {
        List<SearchMatch> results = new ArrayList<>();
        for (IndexedLine line : lines) {
            if (results.size() >= maxResults) break;
            int idx = line.lineText().indexOf(query);
            while (idx >= 0) {
                results.add(new SearchMatch(line.filePath(), line.lineNumber(), line.lineText(), idx, idx + query.length()));
                if (results.size() >= maxResults) return results;
                idx = line.lineText().indexOf(query, idx + query.length());
            }
        }
        return results;
    }

    private static boolean presentInAll(int lineId, List<int[]> postings) {
        for (int[] ids : postings) {
            if (Arrays.binarySearch(ids, lineId) < 0) {
                return false;
            }
        }
        return true;
    }

    private static List<String> gramsForQuery(String query) {
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + GRAM_SIZE <= query.length(); i++) {
            grams.add(query.substring(i, i + GRAM_SIZE));
        }
        return new ArrayList<>(grams);
    }

    private static SearchSnapshot collectSnapshot(Path rootPath) throws IOException {
        List<Path> files = collectSearchableFiles(rootPath);
        long signature = 17;
        for (Path file : files) {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            signature = 31 * signature + Objects.hash(rootPath.relativize(file).toString(), attrs.size(), attrs.lastModifiedTime().toMillis());
        }
        return new SearchSnapshot(files, signature, files.size());
    }

    private static List<Path> collectSearchableFiles(Path rootPath) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path name = dir.getFileName();
                if (name != null && IGNORED_DIRECTORIES.contains(name.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.size() <= MAX_FILE_SIZE_BYTES) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static boolean isMetadataCurrent(Path metadataPath, long signatureHash, int fileCount) {
        if (!Files.exists(metadataPath)) return false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(metadataPath)))) {
            int magic = in.readInt();
            int version = in.readInt();
            long savedHash = in.readLong();
            int savedFileCount = in.readInt();
            return magic == METADATA_MAGIC
                    && version == INDEX_VERSION
                    && savedHash == signatureHash
                    && savedFileCount == fileCount;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeMetadata(Path metadataPath, long signatureHash, int fileCount) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(metadataPath)))) {
            out.writeInt(METADATA_MAGIC);
            out.writeInt(INDEX_VERSION);
            out.writeLong(signatureHash);
            out.writeInt(fileCount);
        }
    }

    private static SearchIndexData buildIndex(Path rootPath, List<Path> files) {
        List<IndexedLine> lines = new ArrayList<>();
        Map<String, IntList> gramToIds = new HashMap<>();

        for (Path file : files) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    int lineId = lines.size();
                    lines.add(new IndexedLine(file, lineNum, line));
                    addLineGrams(line, lineId, gramToIds);
                }
            } catch (MalformedInputException e) {
                // binary or non-UTF-8 file — skip
            } catch (IOException e) {
                // unreadable — skip
            }
        }

        return new SearchIndexData(lines, toImmutablePostingMap(gramToIds));
    }

    private static void addLineGrams(String line, int lineId, Map<String, IntList> gramToIds) {
        if (line.length() < GRAM_SIZE) return;
        Set<String> lineGrams = new HashSet<>();
        for (int i = 0; i + GRAM_SIZE <= line.length(); i++) {
            lineGrams.add(line.substring(i, i + GRAM_SIZE));
        }
        for (String gram : lineGrams) {
            gramToIds.computeIfAbsent(gram, g -> new IntList()).add(lineId);
        }
    }

    private static Map<String, int[]> toImmutablePostingMap(Map<String, IntList> gramToIds) {
        Map<String, int[]> map = new HashMap<>(gramToIds.size());
        for (Map.Entry<String, IntList> entry : gramToIds.entrySet()) {
            map.put(entry.getKey(), entry.getValue().toArray());
        }
        return map;
    }

    private static void writeIndex(Path rootPath, Path indexPath, List<IndexedLine> lines) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(indexPath)))) {
            out.writeInt(INDEX_MAGIC);
            out.writeInt(INDEX_VERSION);
            out.writeInt(lines.size());
            for (IndexedLine line : lines) {
                String relPath;
                try {
                    relPath = rootPath.relativize(line.filePath()).toString();
                } catch (IllegalArgumentException ex) {
                    relPath = line.filePath().toString();
                }
                out.writeUTF(relPath);
                out.writeInt(line.lineNumber());
                out.writeUTF(line.lineText());
            }
        }
    }

    private static SearchIndexData loadIndex(Path rootPath, Path indexPath) throws IOException {
        List<IndexedLine> lines = new ArrayList<>();
        Map<String, IntList> gramToIds = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(indexPath)))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != INDEX_MAGIC || version != INDEX_VERSION) {
                throw new IOException("Unsupported index format");
            }

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String relPath = in.readUTF();
                int lineNumber = in.readInt();
                String lineText = in.readUTF();
                Path filePath = Path.of(relPath);
                if (!filePath.isAbsolute()) {
                    filePath = rootPath.resolve(filePath).normalize();
                }
                int lineId = lines.size();
                lines.add(new IndexedLine(filePath, lineNumber, lineText));
                addLineGrams(lineText, lineId, gramToIds);
            }
        }
        return new SearchIndexData(lines, toImmutablePostingMap(gramToIds));
    }

    private record SearchSnapshot(List<Path> files, long signatureHash, int fileCount) {
    }

    private record IndexedLine(Path filePath, int lineNumber, String lineText) {
    }

    private record SearchIndexData(List<IndexedLine> lines, Map<String, int[]> gramIndex) {
    }

    private static final class IntList {
        private int[] data = new int[16];
        private int size;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static String summarizeQuery(String query) {
        if (query.length() <= 60) {
            return query;
        }
        return query.substring(0, 57) + "...";
    }
}
