package host;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Utility to compute diffs for a file using JGit. Keeps the implementation minimal —
 * it extracts the full file contents for HEAD and working tree and provides a
 * simple line-based hunk model.
 *
 * <p>Preferred construction: pass a ready-made JGit {@link Repository} — obtained from
 * {@code GitManager.getRepository()} — together with the repo root path so that
 * absolute file paths can be relativized correctly.
 */
public class DiffComputer {

    public static record Hunk(int oldStart, int oldLines, int newStart, int newLines, List<String> oldLinesText, List<String> newLinesText) {}
    public static record AlignedRow(int leftLineNumber, int rightLineNumber,
                                    String leftText, String rightText,
                                    boolean leftPlaceholder, boolean rightPlaceholder,
                                    boolean changed) {}
    public static record AlignedDiff(List<AlignedRow> rows, List<Integer> hunkRowStarts) {}

    private final Repository repo;
    private final Path repoRoot; // absolute path to the working-tree root

    /**
     * Preferred constructor: receives an already-opened {@link Repository} and the
     * absolute root path of the working tree (i.e. {@code gitManager.getRootPath()}).
     */
    public DiffComputer(Repository repo, Path repoRoot) {
        this.repo = repo;
        this.repoRoot = repoRoot;
    }

    /**
     * Returns file contents from HEAD (or empty string if not present / untracked).
     *
     * @param file absolute or relative (from repoRoot) path to the file
     */
    public String loadHeadContent(Path file) {
        if (repo == null) {
            System.err.println("[DiffComputer] No repository available");
            return "";
        }
        try {
            // Normalize to relative path from repo root
            Path relPath = file.isAbsolute() ? repoRoot.relativize(file) : file;
            String pathStr = relPath.toString().replace("\\", "/");

            ObjectReader reader = repo.newObjectReader();
            var revWalk = new org.eclipse.jgit.revwalk.RevWalk(repo);
            try {
                var headRef = repo.resolve("HEAD");
                if (headRef == null) return "";

                var revCommit = revWalk.parseCommit(headRef);
                TreeWalk treeWalk = TreeWalk.forPath(reader, pathStr, revCommit.getTree());
                if (treeWalk == null) {
                    System.err.println("[DiffComputer] File not found in HEAD: " + pathStr);
                    return "";
                }
                var objId = treeWalk.getObjectId(0);
                try (var is = repo.open(objId).openStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                revWalk.close();
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("[DiffComputer] Error loading HEAD content for " + file + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Loads working-tree content for the file from disk.  Resolves relative paths
     * against {@code repoRoot}.
     */
    public String loadWorkingTreeContent(Path file) {
        try {
            Path abs = file.isAbsolute() ? file : repoRoot.resolve(file);
            return java.nio.file.Files.readString(abs);
        } catch (Exception e) {
            System.err.println("[DiffComputer] Error loading working tree content for " + file + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Very small line-based diff to produce hunks.
     */
    public List<Hunk> computeLineHunks(String oldText, String newText) {
        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);
        List<Hunk> hunks = new ArrayList<>();

        int i = 0, j = 0;
        while (i < oldLines.length || j < newLines.length) {
            if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                i++; j++; continue;
            }
            int oi = i; int nj = j;
            boolean matched = false;
            for (int a = i; a < Math.min(oldLines.length, i + 200); a++) {
                for (int b = j; b < Math.min(newLines.length, j + 200); b++) {
                    if (oldLines[a].equals(newLines[b])) {
                        oi = a; nj = b; matched = true; break;
                    }
                }
                if (matched) break;
            }
            List<String> oldSegment = new ArrayList<>();
            List<String> newSegment = new ArrayList<>();
            while (i < oi && i < oldLines.length) oldSegment.add(oldLines[i++]);
            while (j < nj && j < newLines.length) newSegment.add(newLines[j++]);
            if (!oldSegment.isEmpty() || !newSegment.isEmpty()) {
                hunks.add(new Hunk(oi + 1 - oldSegment.size(), oldSegment.size(), nj + 1 - newSegment.size(), newSegment.size(), oldSegment, newSegment));
            }
            if (!matched) {
                while (i < oldLines.length) oldSegment.add(oldLines[i++]);
                while (j < newLines.length) newSegment.add(newLines[j++]);
                hunks.add(new Hunk(Math.max(1, oldLines.length - oldSegment.size() + 1), oldSegment.size(),
                                   Math.max(1, newLines.length - newSegment.size() + 1), newSegment.size(),
                                   oldSegment, newSegment));
            }
        }
        return hunks;
    }

    public AlignedDiff alignForSideBySide(String oldText, String newText, List<Hunk> hunks) {
        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);
        List<AlignedRow> rows = new ArrayList<>();
        List<Integer> hunkRowStarts = new ArrayList<>();

        int oldIndex = 0;
        int newIndex = 0;

        for (Hunk hunk : hunks) {
            int oldStartIndex = Math.max(0, hunk.oldStart() - 1);
            int newStartIndex = Math.max(0, hunk.newStart() - 1);

            while (oldIndex < oldStartIndex && newIndex < newStartIndex) {
                rows.add(new AlignedRow(oldIndex + 1, newIndex + 1,
                        oldLines[oldIndex], newLines[newIndex], false, false, false));
                oldIndex++;
                newIndex++;
            }
            while (oldIndex < oldStartIndex) {
                rows.add(new AlignedRow(oldIndex + 1, -1,
                        oldLines[oldIndex], "", false, true, true));
                oldIndex++;
            }
            while (newIndex < newStartIndex) {
                rows.add(new AlignedRow(-1, newIndex + 1,
                        "", newLines[newIndex], true, false, true));
                newIndex++;
            }

            hunkRowStarts.add(rows.size());

            int oldCount = Math.max(0, hunk.oldLines());
            int newCount = Math.max(0, hunk.newLines());
            int rowsInHunk = Math.max(oldCount, newCount);
            for (int i = 0; i < rowsInHunk; i++) {
                boolean hasLeft = i < oldCount && oldIndex + i < oldLines.length;
                boolean hasRight = i < newCount && newIndex + i < newLines.length;

                int leftLineNumber = hasLeft ? oldIndex + i + 1 : -1;
                int rightLineNumber = hasRight ? newIndex + i + 1 : -1;
                String leftText = hasLeft ? oldLines[oldIndex + i] : "";
                String rightText = hasRight ? newLines[newIndex + i] : "";

                rows.add(new AlignedRow(leftLineNumber, rightLineNumber,
                        leftText, rightText, !hasLeft, !hasRight, true));
            }

            oldIndex += oldCount;
            newIndex += newCount;
        }

        while (oldIndex < oldLines.length && newIndex < newLines.length) {
            rows.add(new AlignedRow(oldIndex + 1, newIndex + 1,
                    oldLines[oldIndex], newLines[newIndex], false, false, false));
            oldIndex++;
            newIndex++;
        }
        while (oldIndex < oldLines.length) {
            rows.add(new AlignedRow(oldIndex + 1, -1,
                    oldLines[oldIndex], "", false, true, true));
            oldIndex++;
        }
        while (newIndex < newLines.length) {
            rows.add(new AlignedRow(-1, newIndex + 1,
                    "", newLines[newIndex], true, false, true));
            newIndex++;
        }

        return new AlignedDiff(List.copyOf(rows), List.copyOf(hunkRowStarts));
    }
}
