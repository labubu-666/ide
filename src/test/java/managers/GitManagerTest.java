package managers;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for GitManager class.
 * Tests git repository detection and branch information retrieval.
 */
public class GitManagerTest {

    private Path repo;
    private GitManager manager;

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

    @BeforeEach
    public void setUp() throws Exception {
        repo = createTempGitRepository();
        manager = new GitManager(repo);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
        }
        deleteDirectoryRecursively(repo);
    }

    /**
     * Test that GitManager correctly detects a git repository.
     * Uses the current working directory which is a git repo.
     */
    @Test
    public void testDetectGitRepository() {
        assertThat(manager.isGitRepository()).as("Temp directory should be detected as a git repository").isTrue();
    }

    /**
     * Test that GitManager can retrieve the current branch name.
     */
    @Test
    public void testGetCurrentBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        assertThat(manager.getCurrentBranch().isPresent()).as("Should return a branch name").isTrue();

        String branch = manager.getCurrentBranch().get();
        assertThat(branch.isEmpty()).as("Branch name should not be empty").isFalse();

        // Do not hardcode branch name (may be 'main' or 'master' or other).
        // Just ensure it is non-empty and represents the repository branch.
        assertThat(branch).as("Branch name should be a non-empty string").isNotEmpty();
    }

    /**
     * Test that getDisplayString returns appropriate values.
     */
    @Test
    public void testGetDisplayString() {
        String displayString = manager.getDisplayString();
        assertThat(displayString.isEmpty()).as("Display string should not be empty").isFalse();

        if (manager.isGitRepository()) {
            // Display string should reflect the current branch (may vary across environments)
            String expected = manager.getCurrentBranch().orElse("");
            assertThat(displayString).as("Display string should show branch name when in git repo").isEqualTo(expected);
        }
    }

    /**
     * Test that GitManager handles non-git directories gracefully.
     * This test uses its own setup/teardown since it requires a non-git directory.
     */
    @Test
    public void testNonGitDirectory() throws Exception {
        // Create a temporary directory that's not a git repo
        Path tempDir = Files.createTempDirectory("non-git-test");
        
        try {
            GitManager tempManager = new GitManager(tempDir);
            
            assertThat(tempManager.isGitRepository()).as("Temporary directory should not be detected as git repo").isFalse();
            
            // getCurrentBranch should return empty Optional
            assertThat(tempManager.getCurrentBranch().isPresent()).as("Should return empty optional for non-git directory").isFalse();
            
            // getDisplayString should return empty string for non-git directory
            String displayString = tempManager.getDisplayString();
            assertThat(displayString).as("Should return empty string for non-git directory").isEqualTo("");
            
            tempManager.close();
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
        }
    }

    /**
     * Test that getUnstagedChanges detects modified files correctly.
     */
    @Test
    public void testGetUnstagedChanges() throws Exception {
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
        }
    }

    /**
     * Test hasStagedChanges returns correct values.
     */
    @Test
    public void testHasStagedChanges() throws Exception {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        assertThat(manager.hasStagedChanges()).as("New repo should have no staged changes").isFalse();
        
        // Create and stage a file
        Path testFile = repo.resolve("test-staged.txt");
        try {
            Files.writeString(testFile, "test content");
            manager.stageFile(testFile);
            
            assertThat(manager.hasStagedChanges()).as("Should have staged changes after staging file").isTrue();
            
            // Unstage the file
            manager.unstageFile(testFile);
            assertThat(manager.hasStagedChanges()).as("Should have no staged changes after unstaging").isFalse();
            
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    /**
     * Test hasUnstagedChanges returns correct values.
     */
    @Test
    public void testHasUnstagedChanges() throws Exception {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        assertThat(manager.hasUnstagedChanges()).as("New repo should have no unstaged changes").isFalse();
        
        // Create a new file (unstaged)
        Path testFile = repo.resolve("test-unstaged.txt");
        try {
            Files.writeString(testFile, "test content");
            
            assertThat(manager.hasUnstagedChanges()).as("Should have unstaged changes with new file").isTrue();
            
            // Stage the file
            manager.stageFile(testFile);
            assertThat(manager.hasUnstagedChanges()).as("Should have no unstaged changes after staging").isFalse();
            
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    /**
     * Test getBranches returns list of branches.
     */
    @Test
    public void testGetBranches() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        List<String> branches = manager.getBranches();
        assertThat(branches).as("Should return at least one branch").isNotEmpty();
        
        // Initial branch should be present (main or master)
        assertThat(branches).as("Should contain the initial branch").hasSize(1);
    }

    /**
     * Test creating a new branch.
     */
    @Test
    public void testCreateBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        String newBranchName = "feature-test";
        boolean result = manager.createBranch(newBranchName);
        assertThat(result).as("Should successfully create branch").isTrue();
        
        List<String> branches = manager.getBranches();
        assertThat(branches).as("Should contain the new branch").contains(newBranchName);
    }

    /**
     * Test creating a branch with empty name should fail.
     */
    @Test
    public void testCreateBranchWithEmptyName() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        boolean result = manager.createBranch("");
        assertThat(result).as("Should fail to create branch with empty name").isFalse();
        
        result = manager.createBranch("   ");
        assertThat(result).as("Should fail to create branch with whitespace name").isFalse();
    }

    /**
     * Test checking out a branch.
     */
    @Test
    public void testCheckoutBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        // Create a new branch and check it out
        String newBranchName = "feature-checkout";
        manager.createBranch(newBranchName);
        
        boolean result = manager.checkoutBranch(newBranchName);
        assertThat(result).as("Should successfully checkout branch").isTrue();
        
        assertThat(manager.getCurrentBranch()).as("Current branch should be the new branch")
            .isPresent()
            .hasValue(newBranchName);
    }

    /**
     * Test creating and checking out a branch in one operation.
     */
    @Test
    public void testCreateAndCheckoutBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        String currentBranch = manager.getCurrentBranch().orElse("");
        String newBranchName = "feature-create-checkout";
        
        boolean result = manager.createAndCheckoutBranch(newBranchName);
        assertThat(result).as("Should successfully create and checkout branch").isTrue();
        
        assertThat(manager.getCurrentBranch()).as("Current branch should be the new branch")
            .isPresent()
            .hasValue(newBranchName);
        
        List<String> branches = manager.getBranches();
        assertThat(branches).as("Should contain both branches").contains(currentBranch, newBranchName);
    }

    /**
     * Test creating a branch from another branch.
     */
    @Test
    public void testCreateBranchFrom() throws Exception {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        String currentBranch = manager.getCurrentBranch().orElse("");
        
        // Create a file and commit on current branch
        Path testFile = repo.resolve("base-branch-file.txt");
        Files.writeString(testFile, "content on base branch");
        manager.stageFile(testFile);
        manager.commit("Add file on base branch");
        
        // Create a new branch from current branch
        String newBranchName = "feature-from-base";
        boolean result = manager.createBranchFrom(newBranchName, currentBranch);
        assertThat(result).as("Should successfully create branch from base").isTrue();
        
        assertThat(manager.getCurrentBranch()).as("Should be on the new branch")
            .isPresent()
            .hasValue(newBranchName);
        
        // The file should exist on the new branch
        assertThat(Files.exists(testFile)).as("File from base branch should exist").isTrue();
    }

    /**
     * Test deleting a branch.
     */
    @Test
    public void testDeleteBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        // Create and switch to a new branch
        String branchToDelete = "feature-to-delete";
        manager.createBranch(branchToDelete);
        
        // Switch back to main/master to delete the feature branch
        String currentBranch = manager.getCurrentBranch().orElse("");
        manager.checkoutBranch(currentBranch);
        
        boolean result = manager.deleteBranch(branchToDelete);
        assertThat(result).as("Should successfully delete branch").isTrue();
        
        List<String> branches = manager.getBranches();
        assertThat(branches).as("Should not contain deleted branch").doesNotContain(branchToDelete);
    }

    /**
     * Test that deleting the current branch should fail.
     */
    @Test
    public void testDeleteCurrentBranch() {
        assertThat(manager.isGitRepository()).as("Should be in a git repository").isTrue();
        
        String currentBranch = manager.getCurrentBranch().orElse("");
        
        boolean result = manager.deleteBranch(currentBranch);
        assertThat(result).as("Should fail to delete current branch").isFalse();
        
        List<String> branches = manager.getBranches();
        assertThat(branches).as("Current branch should still exist").contains(currentBranch);
    }
}
