package host.launcher.mounts;

import java.nio.file.Paths;

import host.launcher.ComponentLauncher;
import host.launcher.ComponentMount;
import host.plugins.versioncontrol.git.BranchButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import managers.GitManager;

/**
 * Component preview for {@link BranchButton}.
 * Mounts the button inside a status-bar-like container that matches its real context in Main.
 *
 * Run via Gradle:
 * <pre>
 *   ./gradlew runComponent
 * </pre>
 */
public class BranchButtonMount implements ComponentMount {

    public static void main(String[] args) {
        ComponentLauncher.launch(new BranchButtonMount(), args);
    }

    @Override
    public Node mount(Stage stage) {
        GitManager gitManager = new GitManager(Paths.get(System.getProperty("user.dir")));
        BranchButton button = new BranchButton(gitManager);

        HBox statusBar = new HBox(button);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        return statusBar;
    }

    @Override
    public String title() {
        return "BranchButton — Component Preview";
    }

    @Override
    public double width() {
        return 400;
    }

    @Override
    public double height() {
        return 60;
    }
}
