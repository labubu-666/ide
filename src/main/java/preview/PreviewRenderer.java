package preview;

import host.EditorNavigator;
import java.nio.file.Path;

import javafx.scene.Node;

public interface PreviewRenderer {

    String id();

    String displayName();

    boolean supports(String extension);

    Node render(String text, Path source, EditorNavigator navigator);
}
