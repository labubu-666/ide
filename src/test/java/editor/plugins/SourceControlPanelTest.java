package editor.plugins;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.assertions.api.Assertions;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import editor.plugins.SourceControlPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import managers.GitManager;

@ExtendWith(ApplicationExtension.class)
class SourceControlPanelTest {

    private SourceControlPanel panel;

    /**
     * Will be called with {@code @Before} semantics, i. e. before each test method.
     *
     * @param stage - Will be injected by the test runner.
     */
    @Start
    private void start(Stage stage) {
        // Use current directory for testing
        Path rootPath = Paths.get(System.getProperty("user.dir"));
        GitManager gitManager = new GitManager(rootPath);
        
        panel = new SourceControlPanel(gitManager, v -> {
            // Refresh callback
        });
        
        stage.setScene(new Scene(new StackPane(panel), 400, 600));
        stage.show();
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
     * @param robot - Will be injected by the test runner.
     */
    @Test
    void should_contain_source_control_or_not_git_message(FxRobot robot) {
        // The panel should either show "Source Control" header (if git repo)
        // or "Not a git repository" message (if not git repo)
        boolean hasSourceControlLabel = !robot.lookup("Source Control").queryAll().isEmpty();
        boolean hasNotGitLabel = !robot.lookup("Not a git repository").queryAll().isEmpty();
        
        // At least one of these should be present
        Assertions.assertThat(hasSourceControlLabel || hasNotGitLabel).isTrue();
    }
}
