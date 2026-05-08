package host;

import java.nio.file.Path;

public record EditorNavigationTarget(Path filePath, Integer lineNumber, Integer column) {

    public static EditorNavigationTarget forFile(Path filePath) {
        return new EditorNavigationTarget(filePath, null, null);
    }

    public static EditorNavigationTarget forLocation(Path filePath, int lineNumber, int column) {
        int normalizedLine = Math.max(1, lineNumber);
        int normalizedColumn = Math.max(0, column);
        return new EditorNavigationTarget(filePath, normalizedLine, normalizedColumn);
    }

    public boolean hasPosition() {
        return lineNumber != null && column != null;
    }
}
