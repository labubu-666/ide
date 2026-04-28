package host.launcher;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Generic JavaFX launcher for previewing individual UI components in isolation.
 *
 * <p>Usage — each component preview class calls:
 * <pre>
 *   public static void main(String[] args) {
 *       ComponentLauncher.launch(new MyComponentMount(), args);
 *   }
 * </pre>
 *
 * <p>The static {@code mount} field is set before {@code Application.launch()} so it is
 * available when JavaFX reflectively constructs this class.
 */
public class ComponentLauncher extends Application {

    private static ComponentMount mount;

    /**
     * Launches a component preview window.
     *
     * @param componentMount the mount that builds the component under preview
     * @param args           command-line args forwarded to the JavaFX runtime
     */
    public static void launch(ComponentMount componentMount, String[] args) {
        mount = componentMount;
        Application.launch(ComponentLauncher.class, args);
    }

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(mount.mount(stage));
        stage.setScene(new Scene(root, mount.width(), mount.height()));
        stage.setTitle(mount.title());
        stage.show();
    }
}
