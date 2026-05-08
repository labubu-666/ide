package host;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.eclipse.jgit.api.Git;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import lsp.CompletionProvider;
import lsp.DocumentManager;
import lsp.LspServerRegistry;
import managers.GitManager;
import preview.PreviewRegistry;

@ExtendWith(ApplicationExtension.class)
class DiffEditorTabTest {

    @Test
    void openingDiffJumpsToFirstHunk(@TempDir Path root) throws Exception {
        // Arrange
        Path file = root.resolve("a.txt");
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            git.add().addFilepattern("a.txt").call();
            git.commit()
                    .setMessage("Initial")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call();
        }
        Files.writeString(file, "line1\nLINE2\nline3\n");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        GitManager gitManager = new GitManager(root);
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
                new EditorNavigator() {
                    @Override
                    public void openEditor(EditorNavigationTarget target) {
                    }
                });

        AtomicReference<DiffEditorTab> tabRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();

        try {
            // Act
            Platform.runLater(() -> {
                DiffEditorTab tab = new DiffEditorTab(file, editorContext, gitManager);
                TabPane tabPane = new TabPane(tab);
                Stage stage = new Stage();
                stage.setScene(new Scene(tabPane, 800, 600));
                stage.show();
                tabRef.set(tab);
                stageRef.set(stage);
            });
            waitUntilDiffInitialized(tabRef);

            DiffEditorTab tab = tabRef.get();
            int currentHunkIndex = (int) getField(tab, "currentHunkIndex");
            @SuppressWarnings("unchecked")
            List<Integer> hunkRowStarts = (List<Integer>) getField(tab, "hunkRowStarts");
            CodeArea rightArea = (CodeArea) getField(tab, "rightArea");

            // Assert
            assertThat(currentHunkIndex).isEqualTo(0);
            assertThat(hunkRowStarts).isNotEmpty();
            int firstHunkRow = hunkRowStarts.get(0);
            assertThat(rightArea.getCaretPosition()).isEqualTo(offsetOfLine(rightArea.getText(), firstHunkRow));
        } finally {
            Stage stage = stageRef.get();
            if (stage != null) {
                Platform.runLater(stage::close);
                WaitForAsyncUtils.waitForFxEvents();
            }
            gitManager.close();
            registry.shutdownAll().join();
            executor.shutdownNow();
        }
    }

    private static void waitUntilDiffInitialized(AtomicReference<DiffEditorTab> tabRef) throws Exception {
        for (int i = 0; i < 20; i++) {
            WaitForAsyncUtils.waitForFxEvents();
            DiffEditorTab tab = tabRef.get();
            if (tab != null) {
                int currentHunkIndex = (int) getField(tab, "currentHunkIndex");
                if (currentHunkIndex >= 0) {
                    return;
                }
            }
        }
    }

    private static Object getField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static int offsetOfLine(String text, int line) {
        if (line <= 0) return 0;
        int cnt = 0;
        for (int i = 0; i < text.length(); i++) {
            if (cnt == line) return i;
            if (text.charAt(i) == '\n') cnt++;
        }
        return text.length();
    }
}
