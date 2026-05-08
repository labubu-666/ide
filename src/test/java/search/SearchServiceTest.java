package search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    @Test
    void emptyQueryReturnsNoResults(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hello world");

        List<SearchMatch> results = new SearchService(root).search("");

        assertThat(results).isEmpty();
    }

    @Test
    void findsSingleMatchOnSingleLine(@TempDir Path root) throws IOException {
        Path file = root.resolve("a.txt");
        Files.writeString(file, "the quick brown fox");

        List<SearchMatch> results = new SearchService(root).search("brown");

        assertThat(results).singleElement().satisfies(m -> {
            assertThat(m.filePath()).isEqualTo(file);
            assertThat(m.lineNumber()).isEqualTo(1);
            assertThat(m.lineText()).isEqualTo("the quick brown fox");
            assertThat(m.columnStart()).isEqualTo(10);
            assertThat(m.columnEnd()).isEqualTo(15);
        });
    }

    @Test
    void findsMatchesAcrossLinesAndFiles(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "foo\nbar foo\nbaz");
        Path sub = Files.createDirectory(root.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "nothing here\nfoo at end");

        List<SearchMatch> results = new SearchService(root).search("foo");

        assertThat(results).hasSize(3);
        assertThat(results).extracting(SearchMatch::lineNumber).containsExactlyInAnyOrder(1, 2, 2);
    }

    @Test
    void findsMultipleMatchesOnSameLine(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "ab ab ab");

        List<SearchMatch> results = new SearchService(root).search("ab");

        assertThat(results).hasSize(3);
        assertThat(results).extracting(SearchMatch::columnStart).containsExactly(0, 3, 6);
    }

    @Test
    void skipsIgnoredDirectories(@TempDir Path root) throws IOException {
        Path git = Files.createDirectory(root.resolve(".git"));
        Files.writeString(git.resolve("config"), "needle");
        Path build = Files.createDirectory(root.resolve("build"));
        Files.writeString(build.resolve("artifact.txt"), "needle");
        Files.writeString(root.resolve("real.txt"), "needle");

        List<SearchMatch> results = new SearchService(root).search("needle");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).filePath()).isEqualTo(root.resolve("real.txt"));
    }

    @Test
    void honoursMaxResultsLimit(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "x\nx\nx\nx\nx\n");

        List<SearchMatch> results = new SearchService(root).search("x", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void skipsBinaryFilesGracefully(@TempDir Path root) throws IOException {
        byte[] binary = new byte[]{(byte) 0xC3, 0x28, 0x00, 0x01, (byte) 0xFF};
        Files.write(root.resolve("blob.bin"), binary);
        Files.writeString(root.resolve("text.txt"), "hello");

        List<SearchMatch> results = new SearchService(root).search("hello");

        assertThat(results).singleElement()
                .satisfies(m -> assertThat(m.filePath()).isEqualTo(root.resolve("text.txt")));
    }

    @Test
    void caseSensitiveByDefault(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "Hello hello HELLO");

        List<SearchMatch> results = new SearchService(root).search("hello");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).columnStart()).isEqualTo(6);
    }

    @Test
    void searchUsesStartupIndexWhenAvailable(@TempDir Path root) throws IOException {
        SearchService.clearIndexCacheForTests();
        Files.writeString(root.resolve("a.txt"), "hello indexed world");

        SearchService.initializeIndex(root);
        List<SearchMatch> results = new SearchService(root).search("indexed");

        assertThat(results).singleElement().satisfies(m -> {
            assertThat(m.filePath()).isEqualTo(root.resolve("a.txt"));
            assertThat(m.lineNumber()).isEqualTo(1);
            assertThat(m.columnStart()).isEqualTo(6);
            assertThat(m.columnEnd()).isEqualTo(13);
        });
    }

    @Test
    void startupIndexRebuildsWhenFilesBecomeStale(@TempDir Path root) throws IOException {
        SearchService.clearIndexCacheForTests();
        Path file = root.resolve("a.txt");
        Files.writeString(file, "old-token");
        SearchService.initializeIndex(root);

        Files.writeString(file, "new-token");
        SearchService.initializeIndex(root);
        List<SearchMatch> oldResults = new SearchService(root).search("old-token");
        List<SearchMatch> newResults = new SearchService(root).search("new-token");

        assertThat(oldResults).isEmpty();
        assertThat(newResults).singleElement().satisfies(m -> {
            assertThat(m.filePath()).isEqualTo(file);
            assertThat(m.columnStart()).isEqualTo(0);
        });
    }

    @Test
    void corruptedIndexFailsOpenToParallelScan(@TempDir Path root) throws IOException {
        SearchService.clearIndexCacheForTests();
        Path file = root.resolve("a.txt");
        Files.writeString(file, "fallback-token");
        SearchService.initializeIndex(root);

        Path indexPath = root.resolve(".ide/search-index/search.idx");
        Files.write(indexPath, new byte[]{0x00, 0x01, 0x02, 0x03});

        SearchService.initializeIndex(root);
        List<SearchMatch> results = new SearchService(root).search("fallback-token");

        assertThat(results).singleElement().satisfies(m -> {
            assertThat(m.filePath()).isEqualTo(file);
            assertThat(m.columnStart()).isEqualTo(0);
        });
    }
}
