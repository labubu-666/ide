package host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import org.eclipse.lsp4j.Diagnostic;

import filetype.FileTypeRegistry;
import language.Languages;
import lsp.CompletionProvider;
import lsp.DocumentManager;
import managers.FileManager;
import managers.GitManager;
import preview.PreviewRegistry;
import java.util.concurrent.ExecutorService;

class CenterPanel extends TabPane implements EditorNavigator {

    private final Path rootPath;
    private final EditorContext editorCtx;
    private final DocumentManager docManager;
    private final FileTypeRegistry fileTypes;
    private final GitManager gitManager;
    private final PreviewRegistry previewRegistry;
    private final Consumer<ActiveEditorInfo> onEditorActivated;
    private final Runnable onDiffActivated;
    private final Runnable onStatusRefreshNeeded;
    private final Runnable onTreeRefreshNeeded;

    CenterPanel(Path rootPath, ExecutorService executor, DocumentManager docManager,
                CompletionProvider completionProvider, PreviewRegistry previewRegistry,
                FileTypeRegistry fileTypes, GitManager gitManager,
                Consumer<ActiveEditorInfo> onEditorActivated,
                Runnable onDiffActivated,
                Runnable onStatusRefreshNeeded,
                Runnable onTreeRefreshNeeded) {
        this.rootPath = rootPath;
        this.docManager = docManager;
        this.fileTypes = fileTypes;
        this.gitManager = gitManager;
        this.previewRegistry = previewRegistry;
        this.onEditorActivated = onEditorActivated;
        this.onDiffActivated = onDiffActivated;
        this.onStatusRefreshNeeded = onStatusRefreshNeeded;
        this.onTreeRefreshNeeded = onTreeRefreshNeeded;

        this.editorCtx = new EditorContext(executor, docManager, completionProvider, previewRegistry,
                this::handleViewModeChanged, this);

        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);

        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                handleTabSelected(newVal));

        getTabs().addListener((ListChangeListener<Tab>) c -> {
            while (c.next()) {
                for (Tab t : c.getRemoved()) {
                    if (t instanceof EditorTab et) {
                        if (et.filePath != null) docManager.didClose(et.filePath);
                        et.dispose();
                    }
                }
            }
        });
    }

    // --- File operations ---

    void createNew() {
        int count = getTabs().size();
        String title = count == 0 ? "Untitled" : "Untitled " + (count + 1);
        EditorTab tab = new EditorTab(title, "", "java", null, editorCtx);
        addAndSelect(tab);
    }

    @Override
    public void openEditor(EditorNavigationTarget target) {
        EditorTab tab = openOrCreateEditorTab(target.filePath());
        if (target.hasPosition()) {
            tab.revealPosition(target.lineNumber(), target.column());
        }
    }

    private EditorTab openOrCreateEditorTab(Path path) {
        Path absolutePath = path.isAbsolute() ? path : rootPath.resolve(path);
        var existing = findEditorTab(absolutePath);
        if (existing.isPresent()) {
            getSelectionModel().select(existing.get());
            return existing.get();
        }
        String fileName = absolutePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = (dot > 0) ? fileName.substring(dot + 1) : "";
        try {
            FileManager fileManager = new FileManager();
            FileManager.FileContent fileContent = fileManager.loadFile(absolutePath);
            EditorTab tab = new EditorTab(fileName, fileContent.getContent(), ext, absolutePath, editorCtx);
            tab.initializeWithFileContent(fileContent);
            addAndSelect(tab);
            docManager.didOpen(absolutePath, fileContent.getContent(), Languages.forExtension(ext).languageId());
            return tab;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to open file: " + absolutePath, e);
        }
    }

    void openDiff(Path filePath) {
        var existingDiff = findDiffTab(filePath);
        if (existingDiff.isPresent()) {
            getSelectionModel().select(existingDiff.get());
            return;
        }
        var existingEditor = findEditorTab(filePath);
        if (existingEditor.isPresent()) {
            getSelectionModel().select(existingEditor.get());
            return;
        }
        DiffEditorTab diffTab = new DiffEditorTab(filePath, editorCtx, gitManager);
        getTabs().add(diffTab);
        getSelectionModel().select(diffTab);
    }

    void saveActive() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et) saveTab(et);
    }

    void saveActiveAs() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et) saveTabAs(et);
    }

    // --- View mode ---

    void setViewMode(ViewMode mode) {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et) et.applyViewMode(mode);
    }

    // --- File events ---

    void handleFileRenamed(Path oldPath, Path newPath) {
        for (Tab t : getTabs()) {
            if (t instanceof EditorTab et && oldPath.equals(et.filePath)) {
                et.filePath = newPath;
                et.baseTitle = newPath.getFileName().toString();
                et.setModified(et.modified);
            }
        }
        onStatusRefreshNeeded.run();
    }

    void handleFileDeleted(Path path) {
        getTabs().removeIf(t -> t instanceof EditorTab et && path.equals(et.filePath));
        onStatusRefreshNeeded.run();
    }

    void handleFileDiscarded(Path path) {
        if (!Files.exists(path)) {
            handleFileDeleted(path);
        } else {
            findEditorTab(path).ifPresent(this::reloadTabFromDisk);
            onStatusRefreshNeeded.run();
        }
    }

    // --- LSP diagnostics ---

    void applyDiagnostics(String uri, List<Diagnostic> diagnostics) {
        for (Tab t : getTabs()) {
            if (t instanceof EditorTab et && uri.equals(et.documentUri())) {
                et.setDiagnostics(diagnostics);
                return;
            }
        }
    }

    // --- Status ---

    Optional<Path> getActiveFilePath() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et && et.filePath != null) {
            return Optional.of(et.filePath);
        }
        return Optional.empty();
    }

    // --- Lifecycle ---

    void disposeAll() {
        for (Tab t : getTabs()) {
            if (t instanceof EditorTab et) {
                et.dispose();
            }
        }
    }

    // --- Internal ---

    private void addAndSelect(EditorTab tab) {
        var img = fileTypes.iconFor(fileTypes.forExtension(tab.extension));
        if (img != null) tab.setGraphic(new ImageView(img));
        tab.setContextMenu(buildTabContextMenu(tab));
        getTabs().add(tab);
        getSelectionModel().select(tab);
    }

    private void handleTabSelected(Tab tab) {
        if (tab instanceof EditorTab et) {
            fireEditorActivated(et);
        } else if (tab instanceof DiffEditorTab) {
            onDiffActivated.run();
        }
        onStatusRefreshNeeded.run();
    }

    private void handleViewModeChanged(EditorTab et) {
        fireEditorActivated(et);
    }

    private void fireEditorActivated(EditorTab et) {
        boolean hasPreview = previewRegistry.hasPreview(et.extension);
        onEditorActivated.accept(new ActiveEditorInfo(et.extension, et.currentViewMode, hasPreview));
    }

    private void saveTab(EditorTab tab) {
        if (tab.filePath == null) {
            saveTabAs(tab);
            return;
        }
        try {
            FileManager fileManager = new FileManager();
            String content = tab.codeArea.getText();
            fileManager.saveFile(tab.filePath, content, tab.fileLastModifiedTime);
            tab.markAsSaved(Files.getLastModifiedTime(tab.filePath).toMillis());
            tab.refreshPreview();
            docManager.didSave(tab.filePath);
            onStatusRefreshNeeded.run();
        } catch (FileManager.OptimisticLockException e) {
            new Alert(AlertType.ERROR, "File was modified externally: " + e.getMessage()).showAndWait();
        } catch (IOException e) {
            new Alert(AlertType.ERROR, "Failed to save: " + e.getMessage()).showAndWait();
        }
    }

    private void saveTabAs(EditorTab tab) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save As");
        if (tab.filePath != null) {
            chooser.setInitialDirectory(tab.filePath.getParent().toFile());
            chooser.setInitialFileName(tab.filePath.getFileName().toString());
        } else {
            chooser.setInitialDirectory(rootPath.toFile());
            chooser.setInitialFileName(tab.baseTitle);
        }
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                String content = tab.codeArea.getText();
                Files.writeString(file.toPath(), content);

                if (tab.virtualUri != null) {
                    docManager.didCloseVirtual(tab.virtualUri, tab.extension);
                    tab.virtualUri = null;
                    tab.filePath = file.toPath();
                    docManager.didOpen(tab.filePath, content, Languages.forExtension(tab.extension).languageId());
                } else {
                    tab.filePath = file.toPath();
                }

                tab.baseTitle = file.getName();
                tab.setModified(false);
                onTreeRefreshNeeded.run();
                onStatusRefreshNeeded.run();
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Failed to save: " + e.getMessage()).showAndWait();
            }
        }
    }

    private void reloadTabFromDisk(EditorTab et) {
        try {
            FileManager fileManager = new FileManager();
            FileManager.FileContent fileContent = fileManager.loadFile(et.filePath);
            et.codeArea.replaceText(fileContent.getContent());
            et.initializeWithFileContent(fileContent);
        } catch (IOException e) {
            new Alert(AlertType.ERROR, "Failed to reload file: " + e.getMessage()).showAndWait();
        }
    }

    private Optional<EditorTab> findEditorTab(Path filePath) {
        return getTabs().stream()
                .filter(t -> t instanceof EditorTab et && filePath.equals(et.filePath))
                .map(t -> (EditorTab) t)
                .findFirst();
    }

    private Optional<DiffEditorTab> findDiffTab(Path filePath) {
        return getTabs().stream()
                .filter(t -> t instanceof DiffEditorTab det && filePath.equals(det.filePath))
                .map(t -> (DiffEditorTab) t)
                .findFirst();
    }

    private ContextMenu buildTabContextMenu(Tab tab) {
        MenuItem closeItem = new MenuItem("Close");
        closeItem.setOnAction(e -> TabOperations.close(getTabs(), tab));

        MenuItem closeOthersItem = new MenuItem("Close Others");
        closeOthersItem.setOnAction(e -> TabOperations.closeOthers(getTabs(), tab));

        return new ContextMenu(closeItem, closeOthersItem);
    }
}
