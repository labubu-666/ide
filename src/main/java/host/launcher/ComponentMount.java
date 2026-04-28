package host.launcher;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Contract for a component preview mount.
 * Implement this to make any UI component launchable in isolation via {@link ComponentLauncher}.
 */
public interface ComponentMount {

    /**
     * Builds and returns the component to preview.
     * Called on the JavaFX Application Thread after the stage is ready.
     *
     * @param stage the preview stage (available for dialogs that need a window owner)
     * @return the root node to display
     */
    Node mount(Stage stage);

    /** Window title shown in the preview stage. */
    default String title() {
        return getClass().getSimpleName() + " — Component Preview";
    }

    /** Initial window width in pixels. */
    default double width() {
        return 600;
    }

    /** Initial window height in pixels. */
    default double height() {
        return 120;
    }
}
