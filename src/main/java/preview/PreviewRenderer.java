package preview;

import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.scene.Node;

public interface PreviewRenderer {

    String id();

    String displayName();

    boolean supports(String extension);

    Node render(String text, Path source, Consumer<Path> fileNavigator);
}
