package host;

import java.nio.file.Path;

import search.SearchMatch;

public interface EditorNavigator {

    void openEditor(EditorNavigationTarget target);

    default void openFile(Path path) {
        openEditor(EditorNavigationTarget.forFile(path));
    }

    default void openLocation(Path filePath, int lineNumber, int column) {
        openEditor(EditorNavigationTarget.forLocation(filePath, lineNumber, column));
    }

    default void openSearchMatch(SearchMatch match) {
        openLocation(match.filePath(), match.lineNumber(), match.columnStart());
    }
}
