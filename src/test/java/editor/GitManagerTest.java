package editor;

import static org.assertj.core.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Tests for GitManager class.
 * Tests git repository detection and branch information retrieval.
 */
public class GitManagerTest {

    /**
     * Create a temporary git repository for testing.
     * Initializes a repo, sets user config and creates an initial commit so
     * the repository has a valid branch HEAD.
     */
    private static Path createTempGitRepository() throws Exception {
        Path dir = Files.createTempDirectory("git-test-repo");

        // Initialize git repo
        runGitCommand(dir, "init");

        // Ensure commits can be made
        runGitCommand(dir, "config", "user.email", "test@example.com");
        runGitCommand(dir, "config", "user.name", "Test User");

        // Create an initial commit so HEAD points to a branch
        Files.writeString(dir.resolve("README.md"), "initial commit\n");
        runGitCommand(dir, "add", "README.md");
        runGitCommand(dir, "commit", "-m", "initial commit");

        return dir;
    }

    private static void runGitCommand(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String a : args) cmd.add(a);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String out = r.lines().collect(Collectors.joining("\n"));
            int code = p.waitFor();
            if (code != 0) {
                throw new IOException("git command failed (" + String.join(" ", cmd) + "): exit=" + code + " output=" + out);
            }
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    /**
     * Test that GitManager correctly detects a git repository.
     * Uses the current working directory which is a git repo.
     */
    @Test
    public void testDetectGitRepository() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager manager = new GitManager(repo);

            assertThat(manager.isGitRepository()).as("Temp directory should be detected as a git repository").isTrue();
            manager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    /**
     * Test that GitManager can retrieve the current branch name.
     */
    @Test
    public void testGetCurrentBranch() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager manager = new GitManager(repo);

            assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
            assertThat(manager.getCurrentBranch().isPresent()).as("Should return a branch name").isTrue();

            String branch = manager.getCurrentBranch().get();
            assertThat(branch.isEmpty()).as("Branch name should not be empty").isFalse();

            // Do not hardcode branch name (may be 'main' or 'master' or other).
            // Just ensure it is non-empty and represents the repository branch.
            assertThat(branch).as("Branch name should be a non-empty string").isNotEmpty();

            manager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    /**
     * Test that getDisplayString returns appropriate values.
     */
    @Test
    public void testGetDisplayString() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager manager = new GitManager(repo);

            String displayString = manager.getDisplayString();
            assertThat(displayString.isEmpty()).as("Display string should not be empty").isFalse();

            if (manager.isGitRepository()) {
                // Display string should reflect the current branch (may vary across environments)
                String expected = manager.getCurrentBranch().orElse("");
                assertThat(displayString).as("Display string should show branch name when in git repo").isEqualTo(expected);
            }

            manager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    /**
     * Test that GitManager handles non-git directories gracefully.
     */
    @Test
    public void testNonGitDirectory() throws Exception {
        // Create a temporary directory that's not a git repo
        Path tempDir = Files.createTempDirectory("non-git-test");
        
        try {
            GitManager manager = new GitManager(tempDir);
            
            assertThat(manager.isGitRepository()).as("Temporary directory should not be detected as git repo").isFalse();
            
            // getCurrentBranch should return empty Optional
            assertThat(manager.getCurrentBranch().isPresent()).as("Should return empty optional for non-git directory").isFalse();
            
            // getDisplayString should return empty string for non-git directory
            String displayString = manager.getDisplayString();
            assertThat(displayString).as("Should return empty string for non-git directory").isEqualTo("");
            
            manager.close();
        } finally {
            // Cleanup
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Test that stageFile and unstageFile work with relative paths.
     * This is important because getUnstagedChanges() returns relative paths,
     * and the UI passes these directly to stageFile/unstageFile.
     */
    @Test
    public void testStageUnstageWithRelativePaths() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager manager = new GitManager(repo);

            assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();

            // Create a test file
            Path testFile = repo.resolve("test-relative-path-file.txt");
            Path relativePath = Paths.get("test-relative-path-file.txt");

            try {
                Files.writeString(testFile, "test content for relative path test");

                // File should appear in unstaged changes with relative path
                var unstagedChanges = manager.getUnstagedChanges();
                assertThat(unstagedChanges.containsKey(relativePath)).as("New file should appear in unstaged changes with relative path").isTrue();

                // Stage the file using the RELATIVE path (simulating what SourceControlPanel does)
                assertThat(manager.stageFile(relativePath)).as("Should be able to stage file using relative path").isTrue();

                // Verify file is now staged
                var stagedChanges = manager.getStagedChanges();
                unstagedChanges = manager.getUnstagedChanges();

                assertThat(unstagedChanges.containsKey(relativePath)).as("File should not be in unstaged changes after staging with relative path").isFalse();
                assertThat(stagedChanges.containsKey(relativePath)).as("File should be in staged changes after staging with relative path").isTrue();

                // Unstage using relative path
                assertThat(manager.unstageFile(relativePath)).as("Should be able to unstage file using relative path").isTrue();

                // Verify file is back in unstaged
                unstagedChanges = manager.getUnstagedChanges();
                stagedChanges = manager.getStagedChanges();

                assertThat(unstagedChanges.containsKey(relativePath)).as("File should be back in unstaged changes after unstaging with relative path").isTrue();
                assertThat(stagedChanges.containsKey(relativePath)).as("File should not be in staged changes after unstaging with relative path").isFalse();

            } finally {
                // Cleanup
                Files.deleteIfExists(testFile);
                manager.close();
            }
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    /**
     * Test that getUnstagedChanges detects modified files correctly.
     */
    @Test
    public void testGetUnstagedChanges() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager manager = new GitManager(repo);

            assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();

            // Create a test file
            Path testFile = repo.resolve("test-unstaged-file.txt");
            try {
                Files.writeString(testFile, "test content");

                // File should appear in unstaged changes as untracked
                var unstagedChanges = manager.getUnstagedChanges();
                Path relativeTestFile = Paths.get("test-unstaged-file.txt");

                assertThat(unstagedChanges.containsKey(relativeTestFile)).as("New file should appear in unstaged changes. Found: %s", unstagedChanges.keySet()).isTrue();
                assertThat(unstagedChanges.get(relativeTestFile)).as("New file should have status 'U' (untracked)").isEqualTo("U");

                // Stage the file
                assertThat(manager.stageFile(testFile)).as("Should be able to stage the test file").isTrue();

                // File should now be in staged changes, not unstaged
                var stagedChanges = manager.getStagedChanges();
                unstagedChanges = manager.getUnstagedChanges();

                assertThat(unstagedChanges.containsKey(relativeTestFile)).as("Staged file should not appear in unstaged changes").isFalse();
                assertThat(stagedChanges.containsKey(relativeTestFile)).as("Staged file should appear in staged changes. Found: %s", stagedChanges.keySet()).isTrue();
                assertThat(stagedChanges.get(relativeTestFile)).as("Staged new file should have status 'A' (added)").isEqualTo("A");

                // Unstage the file
                assertThat(manager.unstageFile(testFile)).as("Should be able to unstage the test file").isTrue();

                // File should be back in unstaged changes
                unstagedChanges = manager.getUnstagedChanges();
                assertThat(unstagedChanges.containsKey(relativeTestFile)).as("Unstaged file should appear back in unstaged changes").isTrue();

            } finally {
                // Cleanup - delete test file
                Files.deleteIfExists(testFile);
                manager.close();
            }
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }
}
