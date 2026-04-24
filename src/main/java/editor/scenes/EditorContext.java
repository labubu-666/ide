package editor.scenes;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import lsp.CompletionProvider;
import lsp.DocumentManager;
import preview.PreviewRegistry;

record EditorContext(
    ExecutorService executor,
    DocumentManager docManager,
    CompletionProvider completionProvider,
    PreviewRegistry previewRegistry,
    Consumer<EditorTab> onViewModeChanged,
    Consumer<Path> onOpenFile
) {}
