package host;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditorNavigationTargetTest {

    @Test
    void forFileCreatesTargetWithoutPosition() {
        Path filePath = Path.of("src/Main.java");

        EditorNavigationTarget target = EditorNavigationTarget.forFile(filePath);

        assertThat(target.filePath()).isEqualTo(filePath);
        assertThat(target.hasPosition()).isFalse();
        assertThat(target.lineNumber()).isNull();
        assertThat(target.column()).isNull();
    }

    @Test
    void forLocationNormalizesInvalidLineAndColumn() {
        Path filePath = Path.of("src/Main.java");

        EditorNavigationTarget target = EditorNavigationTarget.forLocation(filePath, 0, -4);

        assertThat(target.filePath()).isEqualTo(filePath);
        assertThat(target.hasPosition()).isTrue();
        assertThat(target.lineNumber()).isEqualTo(1);
        assertThat(target.column()).isEqualTo(0);
    }

    @Test
    void forLocationKeepsValidPositionValues() {
        Path filePath = Path.of("src/Main.java");

        EditorNavigationTarget target = EditorNavigationTarget.forLocation(filePath, 42, 7);

        assertThat(target.lineNumber()).isEqualTo(42);
        assertThat(target.column()).isEqualTo(7);
    }
}
