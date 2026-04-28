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
}
