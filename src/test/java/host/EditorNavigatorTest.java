package host;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import search.SearchMatch;

import static org.assertj.core.api.Assertions.assertThat;

class EditorNavigatorTest {

    @Test
    void openFileDelegatesToOpenEditorWithFileTarget() {
        CapturingNavigator navigator = new CapturingNavigator();
        Path filePath = Path.of("src/Main.java");

        navigator.openFile(filePath);

        assertThat(navigator.lastTarget.filePath()).isEqualTo(filePath);
        assertThat(navigator.lastTarget.hasPosition()).isFalse();
    }

    @Test
    void openLocationDelegatesToOpenEditorWithLocationTarget() {
        CapturingNavigator navigator = new CapturingNavigator();
        Path filePath = Path.of("src/Main.java");

        navigator.openLocation(filePath, 5, 2);

        assertThat(navigator.lastTarget.filePath()).isEqualTo(filePath);
        assertThat(navigator.lastTarget.lineNumber()).isEqualTo(5);
        assertThat(navigator.lastTarget.column()).isEqualTo(2);
    }

    @Test
    void openSearchMatchDelegatesToOpenEditorWithMatchLocation() {
        CapturingNavigator navigator = new CapturingNavigator();
        SearchMatch match = new SearchMatch(Path.of("src/Main.java"), 8, "hello", 3, 5);

        navigator.openSearchMatch(match);

        assertThat(navigator.lastTarget.filePath()).isEqualTo(match.filePath());
        assertThat(navigator.lastTarget.lineNumber()).isEqualTo(match.lineNumber());
        assertThat(navigator.lastTarget.column()).isEqualTo(match.columnStart());
    }

    private static final class CapturingNavigator implements EditorNavigator {
        private EditorNavigationTarget lastTarget;

        @Override
        public void openEditor(EditorNavigationTarget target) {
            this.lastTarget = target;
        }
    }
}
