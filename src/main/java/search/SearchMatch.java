package search;

import java.nio.file.Path;

public record SearchMatch(
        Path filePath,
        int lineNumber,
        String lineText,
        int columnStart,
        int columnEnd) {
}
