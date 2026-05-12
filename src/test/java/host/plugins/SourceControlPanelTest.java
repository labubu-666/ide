package host.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.assertions.api.Assertions;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import host.plugins.versioncontrol.git.SourceControlPanel;
import host.plugins.versioncontrol.git.SourceControlPanel.Header;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import managers.GitManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class SourceControlPanelTest {

    private SourceControlPanel panel;
    private Path tempGitRepo;
    private GitManager gitManager;

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
     * Will be called with {@code @Before} semantics, i. e. before each test method.
     *
     * @param stage - Will be injected by the test runner.
     */
    @Start
    private void start(Stage stage) throws Exception {
        // Create a temporary git repository for each test to ensure test isolation
        tempGitRepo = createTempGitRepository();
        gitManager = new GitManager(tempGitRepo);
        
        panel = new SourceControlPanel(gitManager, v -> {
            // Refresh callback
        }, null, null);
        
        stage.setScene(new Scene(new StackPane(panel), 400, 600));
        stage.show();
    }

    /**
     * Clean up after each test by closing GitManager and deleting the temp repo.
     */
    @AfterEach
    void tearDown() throws Exception {
        if (gitManager != null) {
            gitManager.close();
        }
        if (tempGitRepo != null) {
            deleteDirectoryRecursively(tempGitRepo);
        }
    }

    /**
     * Test that the panel is created and visible.
     * @param robot - Will be injected by the test runner.
     */
    @Test
    void should_display_source_control_panel(FxRobot robot) {
        Assertions.assertThat(panel).isNotNull();
        Assertions.assertThat(panel.isVisible()).isTrue();
    }

    /**
     * Test that the panel shows appropriate content based on git status.
     * Since we're using a temp git repo, it should show "Source Control" header.
     * @param robot - Will be injected by the test runner.
     */
    @Test
    void should_contain_source_control_header(FxRobot robot) {
        // With a valid git repo (from @Start), we should see "Source Control" header
        boolean hasSourceControlLabel = !robot.lookup("Source Control").queryAll().isEmpty();
        
        Assertions.assertThat(hasSourceControlLabel)
            .as("Panel should show 'Source Control' header in a git repository")
            .isTrue();
    }

    /**
     * Test that the panel shows "Not a git repository" message when in a non-git directory.
     * This test creates its own non-git temporary directory to verify the error handling.
     */
    @Test
    void should_show_not_git_message_in_non_git_directory(FxRobot robot) throws Exception {
        // Create a temp directory WITHOUT initializing git
        Path nonGitDir = Files.createTempDirectory("non-git-test");
        try {
            GitManager nonGitManager = new GitManager(nonGitDir);
            
            // Verify it's not a git repository
            assertThat(nonGitManager.isGitRepository()).isFalse();
            
            // Create a SourceControlPanel with the non-git directory on the JavaFX thread
            final Stage[] testStageHolder = new Stage[1];
            WaitForAsyncUtils.asyncFx(() -> {
                SourceControlPanel nonGitPanel = new SourceControlPanel(nonGitManager, v -> {}, null, null);
                
                // Add the panel to a new stage so we can query it with FxRobot
                Stage testStage = new Stage();
                testStage.setScene(new Scene(new StackPane(nonGitPanel), 400, 600));
                testStage.show();
                testStageHolder[0] = testStage;
            });
            WaitForAsyncUtils.waitForFxEvents();
            
            // Verify the "Not a git repository" message is displayed
            boolean hasNotGitLabel = !robot.lookup("Not a git repository").queryAll().isEmpty();
            assertThat(hasNotGitLabel)
                .as("Panel should show 'Not a git repository' message when not in a git directory")
                .isTrue();
            
            // Clean up
            WaitForAsyncUtils.asyncFx(() -> {
                if (testStageHolder[0] != null) {
                    testStageHolder[0].close();
                }
            });
            WaitForAsyncUtils.waitForFxEvents();
            nonGitManager.close();
        } finally {
            deleteDirectoryRecursively(nonGitDir);
        }
    }

    @Test
    void header_should_display_title_label() {
        Header header = new Header();
        
        Label titleLabel = header.getTitleLabel();
        
        assertThat(titleLabel).isNotNull();
        assertThat(titleLabel.getText()).isEqualTo("Source Control");
    }

    @Test
    void header_should_display_refresh_button() {
        Header header = new Header();
        
        Button refreshButton = header.getRefreshButton();
        
        assertThat(refreshButton).isNotNull();
        assertThat(refreshButton.getText()).isEqualTo("Refresh");
    }

    @Test
    void header_should_notify_single_refresh_listener() {
        Header header = new Header();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        header.addRefreshListener(v -> callbackInvoked.set(true));
        header.getRefreshButton().fire();
        
        assertThat(callbackInvoked.get()).isTrue();
    }

    @Test
    void header_should_notify_multiple_refresh_listeners() {
        Header header = new Header();
        AtomicInteger callbackCount = new AtomicInteger(0);
        
        header.addRefreshListener(v -> callbackCount.incrementAndGet());
        header.addRefreshListener(v -> callbackCount.incrementAndGet());
        header.addRefreshListener(v -> callbackCount.incrementAndGet());
        
        header.getRefreshButton().fire();
        
        assertThat(callbackCount.get()).isEqualTo(3);
    }

    @Test
    void header_should_stop_notifying_removed_listener() {
        Header header = new Header();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        Consumer<Void> listener = v -> callbackInvoked.set(true);
        header.addRefreshListener(listener);
        header.removeRefreshListener(listener);
        
        header.getRefreshButton().fire();
        
        assertThat(callbackInvoked.get()).isFalse();
    }

    @Test
    void header_should_have_correct_node_ids() {
        Header header = new Header();
        
        assertThat(header.getId()).isEqualTo("sourceControlHeader");
        assertThat(header.getTitleLabel().getId()).isEqualTo("headerTitle");
        assertThat(header.getRefreshButton().getId()).isEqualTo("refreshButton");
    }

    @Test
    void header_should_have_correct_styling() {
        Header header = new Header();
        
        // Check title styling
        String titleStyle = header.getTitleLabel().getStyle();
        assertThat(titleStyle).contains("-fx-font-size: 14px");
        assertThat(titleStyle).contains("-fx-font-weight: bold");
        
        // Check button styling
        String buttonStyle = header.getRefreshButton().getStyle();
        assertThat(buttonStyle).contains("-fx-padding: 3 8 3 8");
        assertThat(buttonStyle).contains("-fx-font-size: 11px");
    }

    /**
     * Test that a file moves through the complete workflow:
     * unstaged changes -> staged changes -> committed
     * 
     * This test verifies the full integration with GitManager and ensures
     * the SourceControlPanel correctly tracks file status changes.
     */
    @Test
    void should_move_file_from_unstaged_to_staged_to_committed() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager gitManager = new GitManager(repo);
            
            // Create a SourceControlPanel with the temp repo
            AtomicBoolean refreshCallbackInvoked = new AtomicBoolean(false);
            SourceControlPanel panel = new SourceControlPanel(gitManager, v -> {
                refreshCallbackInvoked.set(true);
            }, null, null);
            
            // Create a new test file
            Path testFile = repo.resolve("test-file.txt");
            Files.writeString(testFile, "test content for workflow");
            
            // Refresh the panel to detect the new file
            panel.refreshStatus();
            
            // Verify file appears in unstaged changes
            var unstagedChanges = gitManager.getUnstagedChanges();
            Path relativeTestFile = Paths.get("test-file.txt");
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("New file should appear in unstaged changes")
                .isTrue();
            assertThat(unstagedChanges.get(relativeTestFile))
                .as("New file should have status 'U' (untracked)")
                .isEqualTo("U");
            
            // Stage the file
            boolean stageResult = gitManager.stageFile(relativeTestFile);
            assertThat(stageResult).as("Should successfully stage the file").isTrue();
            
            // Refresh the panel again
            panel.refreshStatus();
            
            // Verify file moved from unstaged to staged
            unstagedChanges = gitManager.getUnstagedChanges();
            var stagedChanges = gitManager.getStagedChanges();
            
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("File should not be in unstaged changes after staging")
                .isFalse();
            assertThat(stagedChanges.containsKey(relativeTestFile))
                .as("File should be in staged changes after staging")
                .isTrue();
            assertThat(stagedChanges.get(relativeTestFile))
                .as("Staged new file should have status 'A' (added)")
                .isEqualTo("A");
            
            // Commit the staged file
            boolean commitResult = gitManager.commit("Test commit message");
            assertThat(commitResult).as("Should successfully commit the staged file").isTrue();
            
            // Refresh the panel one more time
            panel.refreshStatus();
            
            // Verify file is no longer in staged changes (committed)
            stagedChanges = gitManager.getStagedChanges();
            unstagedChanges = gitManager.getUnstagedChanges();
            
            assertThat(stagedChanges.containsKey(relativeTestFile))
                .as("File should not be in staged changes after commit")
                .isFalse();
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("File should not be in unstaged changes after commit")
                .isFalse();
            
            // Verify the file is actually committed by checking git log
            assertThat(stagedChanges.isEmpty()).as("There should be no staged changes after commit").isTrue();
            assertThat(unstagedChanges.isEmpty()).as("There should be no unstaged changes after commit").isTrue();
            
            gitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    @Test
    void should_discard_changes_to_modified_tracked_file() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager gitManager = new GitManager(repo);

            // Modify the committed README.md
            Files.writeString(repo.resolve("README.md"), "modified content\n");
            Path relativeFile = Paths.get("README.md");

            var unstagedChanges = gitManager.getUnstagedChanges();
            assertThat(unstagedChanges.get(relativeFile))
                .as("README.md should appear as modified")
                .isEqualTo("M");

            boolean result = gitManager.discardChanges(relativeFile);
            assertThat(result).as("discardChanges should succeed").isTrue();

            unstagedChanges = gitManager.getUnstagedChanges();
            assertThat(unstagedChanges.containsKey(relativeFile))
                .as("File should no longer appear in unstaged changes after discard")
                .isFalse();

            gitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    @Test
    void should_discard_untracked_file_by_deleting_it() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager gitManager = new GitManager(repo);

            Path newFile = repo.resolve("new-untracked.txt");
            Files.writeString(newFile, "untracked content\n");
            Path relativeFile = Paths.get("new-untracked.txt");

            var unstagedChanges = gitManager.getUnstagedChanges();
            assertThat(unstagedChanges.get(relativeFile))
                .as("New file should appear as untracked")
                .isEqualTo("U");

            boolean result = gitManager.discardChanges(relativeFile);
            assertThat(result).as("discardChanges should succeed").isTrue();
            assertThat(Files.exists(newFile))
                .as("Untracked file should be deleted after discard")
                .isFalse();

            gitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    /**
     * Test that a file can be moved from staged back to unstaged changes.
     * This verifies the unstaging functionality works correctly in the SourceControlPanel.
     */
    @Test
    void should_move_file_from_staged_to_unstaged() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager gitManager = new GitManager(repo);
            
            // Create a SourceControlPanel with the temp repo
            SourceControlPanel panel = new SourceControlPanel(gitManager, v -> {}, null, null);
            
            // Create a new test file
            Path testFile = repo.resolve("unstage-test.txt");
            Files.writeString(testFile, "test content for unstaging");
            Path relativeTestFile = Paths.get("unstage-test.txt");
            
            // Refresh the panel to detect the new file
            panel.refreshStatus();
            
            // Verify file appears in unstaged changes
            var unstagedChanges = gitManager.getUnstagedChanges();
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("New file should appear in unstaged changes")
                .isTrue();
            
            // Stage the file
            boolean stageResult = gitManager.stageFile(relativeTestFile);
            assertThat(stageResult).as("Should successfully stage the file").isTrue();
            
            // Refresh and verify file is now staged
            panel.refreshStatus();
            var stagedChanges = gitManager.getStagedChanges();
            unstagedChanges = gitManager.getUnstagedChanges();
            
            assertThat(stagedChanges.containsKey(relativeTestFile))
                .as("File should be in staged changes after staging")
                .isTrue();
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("File should not be in unstaged changes after staging")
                .isFalse();
            
            // Now unstage the file
            boolean unstageResult = gitManager.unstageFile(relativeTestFile);
            assertThat(unstageResult).as("Should successfully unstage the file").isTrue();
            
            // Refresh and verify file moved back to unstaged
            panel.refreshStatus();
            stagedChanges = gitManager.getStagedChanges();
            unstagedChanges = gitManager.getUnstagedChanges();
            
            assertThat(stagedChanges.containsKey(relativeTestFile))
                .as("File should not be in staged changes after unstaging")
                .isFalse();
            assertThat(unstagedChanges.containsKey(relativeTestFile))
                .as("File should be back in unstaged changes after unstaging")
                .isTrue();
            assertThat(unstagedChanges.get(relativeTestFile))
                .as("Unstaged file should have status 'U' (untracked)")
                .isEqualTo("U");
            
            gitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    @Test
    void should_render_unstaged_changes_as_hierarchical_tree() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager localGitManager = new GitManager(repo);
            SourceControlPanel localPanel = new SourceControlPanel(localGitManager, v -> {}, null, null);

            Path nestedFile = repo.resolve("hierarchy-parent").resolve("hierarchy-child").resolve("deep-file.txt");
            Files.createDirectories(nestedFile.getParent());
            Files.writeString(nestedFile, "tree test content");

            localPanel.refreshStatus();

            TreeView<?> changesTree = getPrivateField(localPanel, "changesTree", TreeView.class);
            TreeItem<?> root = changesTree.getRoot();

            TreeItem<?> parentFolder = findChildByName(root, "hierarchy-parent");
            assertThat(parentFolder)
                .as("Top-level folder should be present in changes tree")
                .isNotNull();
            assertThat(nodeIsFolder(parentFolder))
                .as("Top-level hierarchy node should be a folder")
                .isTrue();

            TreeItem<?> childFolder = findChildByName(parentFolder, "hierarchy-child");
            assertThat(childFolder)
                .as("Nested folder should be present under top-level folder")
                .isNotNull();
            assertThat(nodeIsFolder(childFolder))
                .as("Nested hierarchy node should be a folder")
                .isTrue();

            TreeItem<?> fileLeaf = findChildByName(childFolder, "deep-file.txt");
            assertThat(fileLeaf)
                .as("File leaf should be present under nested folder")
                .isNotNull();
            assertThat(nodeIsFolder(fileLeaf))
                .as("File node should not be marked as folder")
                .isFalse();

            localGitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    @Test
    void should_keep_folder_nodes_structural_only_in_changes_tree() throws Exception {
        Path repo = createTempGitRepository();
        try {
            GitManager localGitManager = new GitManager(repo);
            SourceControlPanel localPanel = new SourceControlPanel(localGitManager, v -> {}, null, null);

            Path nestedFile = repo.resolve("tree-actions-parent").resolve("tree-actions-child").resolve("leaf.txt");
            Files.createDirectories(nestedFile.getParent());
            Files.writeString(nestedFile, "folder action test");

            localPanel.refreshStatus();

            TreeView<?> changesTree = getPrivateField(localPanel, "changesTree", TreeView.class);
            TreeItem<?> root = changesTree.getRoot();

            TreeItem<?> parentFolder = findChildByName(root, "tree-actions-parent");
            TreeItem<?> childFolder = findChildByName(parentFolder, "tree-actions-child");
            TreeItem<?> fileLeaf = findChildByName(childFolder, "leaf.txt");

            assertThat(nodeChange(parentFolder))
                .as("Folder nodes should not carry file change payload")
                .isNull();
            assertThat(nodeChange(childFolder))
                .as("Nested folder nodes should not carry file change payload")
                .isNull();
            assertThat(nodeChange(fileLeaf))
                .as("File leaf should carry file change payload")
                .isNotNull();

            localGitManager.close();
        } finally {
            deleteDirectoryRecursively(repo);
        }
    }

    private static TreeItem<?> findChildByName(TreeItem<?> parent, String expectedName) throws Exception {
        if (parent == null) {
            return null;
        }

        for (TreeItem<?> child : parent.getChildren()) {
            if (expectedName.equals(nodeName(child))) {
                return child;
            }
        }
        return null;
    }

    private static String nodeName(TreeItem<?> treeItem) throws Exception {
        Object node = treeItem.getValue();
        return (String) invokeNodeMethod(node, "name");
    }

    private static boolean nodeIsFolder(TreeItem<?> treeItem) throws Exception {
        Object node = treeItem.getValue();
        return (boolean) invokeNodeMethod(node, "folder");
    }

    private static Object nodeChange(TreeItem<?> treeItem) throws Exception {
        Object node = treeItem.getValue();
        return invokeNodeMethod(node, "change");
    }

    private static Object invokeNodeMethod(Object node, String methodName) throws Exception {
        var method = node.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(node);
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> fieldType) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return fieldType.cast(field.get(target));
    }
}
