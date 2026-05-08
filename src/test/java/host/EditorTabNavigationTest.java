package host;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import lsp.CompletionProvider;
import lsp.DocumentManager;
import lsp.LspServerRegistry;
import preview.PreviewRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class EditorTabNavigationTest {

    @Test
    void revealPositionRemainsAtRequestedLocationAfterInitialization(@TempDir Path root) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            LspServerRegistry registry = new LspServerRegistry(root, (uri, diagnostics) -> {
            });
            DocumentManager docManager = new DocumentManager(registry);
            CompletionProvider completionProvider = new CompletionProvider(registry);
            PreviewRegistry previewRegistry = new PreviewRegistry();
            EditorContext editorContext = new EditorContext(
                    executor,
                    docManager,
                    completionProvider,
                    previewRegistry,
                    tab -> {
                    },
                    target -> {
                    });

            AtomicReference<EditorTab> tabRef = new AtomicReference<>();
            String text = "first line\nsecond line\nthird line";

            Platform.runLater(() -> {
                EditorTab tab = new EditorTab("a.txt", text, "txt", root.resolve("a.txt"), editorContext);
                tab.revealPosition(2, 3);
                tabRef.set(tab);
            });

            WaitForAsyncUtils.waitForFxEvents();

            EditorTab tab = tabRef.get();
            assertThat(tab).isNotNull();
            assertThat(tab.codeArea.getCaretPosition()).isEqualTo(14);

            Platform.runLater(tab::dispose);
            WaitForAsyncUtils.waitForFxEvents();
        } finally {
            executor.shutdownNow();
        }
    }
}
