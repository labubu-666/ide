package editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ContextMenu;
import javafx.stage.FileChooser;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import managers.FileManager;
import managers.GitManager;
import settings.Settings;
import editor.plugins.versioncontrol.git.BranchButton;
import filetype.FileTypeRegistry;
import language.Languages;
import lsp.CompletionProvider;
import lsp.DocumentManager;
import lsp.LspServerRegistry;
import preview.PreviewRegistry;
import settings.SettingsDialog;
import utils.FileUtils;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private TabPane tabPane;
    private ExecutorService executor;
    private Stage primaryStage;
    private Path rootPath;
    private LspServerRegistry lspRegistry;
    private DocumentManager docManager;
    private FileTypeRegistry fileTypes;
    private CompletionProvider completionProvider;
    private Label statusTypeLabel;
    private Label statusSizeLabel;
    private Label statusBranchLabel;
    private PreviewRegistry previewRegistry;
    private RadioMenuItem viewEditorItem;
    private RadioMenuItem viewSplitItem;
    private RadioMenuItem viewPreviewItem;
    private SplitPane mainSplitPane;
    private LeftPanel leftPanel;
    private RightPanel rightPanel;
    private EditorContext editorCtx;
    private GitManager gitManager;
    private BranchButton branchButton;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        executor = Executors.newSingleThreadExecutor();
        java.util.List<String> params = getParameters().getRaw();
        rootPath = (!params.isEmpty()
                ? Paths.get(params.get(0))
                : Paths.get("")).toAbsolutePath().normalize();

        if (!FileUtils.isValidDirectory(rootPath)) {
            String errorMessage = FileUtils.getValidationError(rootPath);
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Invalid Directory");
            alert.setHeaderText("Cannot Open Directory");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            System.exit(1);
        }

        Settings.load(rootPath);
        gitManager = new GitManager(rootPath);
        lspRegistry = new LspServerRegistry(rootPath, (uri, diagnostics) ->
            Platform.runLater(() -> tabPane.getTabs().stream()
                .filter(t -> t instanceof EditorTab)
                .map(t -> (EditorTab) t)
                .filter(et -> et.filePath != null && et.filePath.toUri().toString().equals(uri))
                .findFirst()
                .ifPresent(et -> et.setDiagnostics(diagnostics))));
        docManager = new DocumentManager(lspRegistry);
        completionProvider = new CompletionProvider(lspRegistry);
        fileTypes = new FileTypeRegistry();
        previewRegistry = new PreviewRegistry();

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        editorCtx = new EditorContext(executor, docManager, completionProvider, previewRegistry,
                this::updateViewMenu, this::openOrFocusFile);

        MenuBar menuBar = buildMenuBar();

        leftPanel = new LeftPanel(rootPath, fileTypes,
                this::openOrFocusFile,
                this::openDiffTab,
                this::handleFileRenamed,
                this::handleFileDeleted,
                this::handleFileDiscarded,
                pos -> mainSplitPane.setDividerPositions(pos, 0.75),
                gitManager);

        rightPanel = new RightPanel(pos -> mainSplitPane.setDividerPositions(0.20, pos));

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal instanceof EditorTab et) {
                updateStylesheet(et.extension);
                updateViewMenu(et);
            }
            // also update view menu when selecting a DiffEditorTab to clear preview controls
            if (newVal instanceof DiffEditorTab) {
                // nothing specific yet, but clear stylesheet to avoid preview errors
                Scene scene = tabPane.getScene();
                if (scene != null) scene.getStylesheets().clear();
            }
            refreshStatus();
        });

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            while (c.next()) {
                for (Tab t : c.getRemoved()) {
                    if (t instanceof EditorTab et) {
                        if (et.filePath != null) docManager.didClose(et.filePath);
                        et.dispose();
                    }
                }
            }
        });

        mainSplitPane = new SplitPane(leftPanel, tabPane, rightPanel);
        SplitPane.setResizableWithParent(leftPanel, Boolean.FALSE);
        SplitPane.setResizableWithParent(rightPanel, Boolean.FALSE);
        mainSplitPane.setDividerPositions(0.25, 1.0 - 0.025);
        mainSplitPane.setStyle("-fx-padding: 0;");

        statusTypeLabel = new Label();
        statusSizeLabel = new Label();
        statusBranchLabel = new Label();
        Separator statusSep = new Separator(Orientation.VERTICAL);
        Separator statusSep2 = new Separator(Orientation.VERTICAL);
        
        // Git branch button with icon (on the left)
        branchButton = new BranchButton(gitManager);
        
        // Spacer to push file info to the right
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox statusBar = new HBox(8, branchButton, spacer, statusTypeLabel, statusSep, statusSizeLabel);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        VBox root = new VBox(menuBar, mainSplitPane, statusBar);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        root.setPrefSize(1024, 768);

        Scene scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN).match(event)) {
                saveCurrentTab();
                event.consume();
            }
        });

        primaryStage.setScene(scene);
        primaryStage.setTitle("IDE - " + rootPath.toString());
        primaryStage.show();
    }

    private MenuBar buildMenuBar() {
        MenuItem newItem = new MenuItem("New");
        newItem.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
        newItem.setOnAction(e -> createNewTab());

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
        saveItem.setOnAction(e -> saveCurrentTab());

        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+S"));
        saveAsItem.setOnAction(e -> saveCurrentTabAs());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(newItem, new SeparatorMenuItem(), saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);

        ToggleGroup viewToggle = new ToggleGroup();
        viewEditorItem = new RadioMenuItem("Editor");
        viewEditorItem.setToggleGroup(viewToggle);
        viewEditorItem.setSelected(true);
        viewEditorItem.setAccelerator(KeyCombination.keyCombination("Shortcut+1"));
        viewEditorItem.setOnAction(e -> {
            Tab sel = tabPane.getSelectionModel().getSelectedItem();
            if (sel instanceof EditorTab et) et.applyViewMode(ViewMode.EDITOR);
        });

        viewSplitItem = new RadioMenuItem("Split");
        viewSplitItem.setToggleGroup(viewToggle);
        viewSplitItem.setAccelerator(KeyCombination.keyCombination("Shortcut+2"));
        viewSplitItem.setOnAction(e -> {
            Tab sel = tabPane.getSelectionModel().getSelectedItem();
            if (sel instanceof EditorTab et) et.applyViewMode(ViewMode.SPLIT);
        });

        viewPreviewItem = new RadioMenuItem("Preview");
        viewPreviewItem.setToggleGroup(viewToggle);
        viewPreviewItem.setAccelerator(KeyCombination.keyCombination("Shortcut+3"));
        viewPreviewItem.setOnAction(e -> {
            Tab sel = tabPane.getSelectionModel().getSelectedItem();
            if (sel instanceof EditorTab et) et.applyViewMode(ViewMode.PREVIEW);
        });

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(viewEditorItem, viewSplitItem, viewPreviewItem);

        MenuItem settingsItem = new MenuItem("Preferences...");
        settingsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+,"));
        settingsItem.setOnAction(e -> new SettingsDialog(primaryStage).show());

        Menu settingsMenu = new Menu("Settings");
        settingsMenu.getItems().add(settingsItem);

        return new MenuBar(fileMenu, viewMenu, settingsMenu);
    }

    private void createNewTab() {
        int count = tabPane.getTabs().size();
        String title = count == 0 ? "Untitled" : "Untitled " + (count + 1);
        EditorTab tab = new EditorTab(title, "", "java", null, editorCtx);
        var img = fileTypes.iconFor(fileTypes.forExtension("java"));
        if (img != null) tab.setGraphic(new ImageView(img));
        tab.setContextMenu(buildTabContextMenu(tab));
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        updateStylesheet("java");
    }

    private void openOrFocusFile(Path path) {
        // Resolve relative paths relative to the project root for matching
        Path absolutePath = path.isAbsolute() ? path : rootPath.resolve(path);
        
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && absolutePath.equals(et.filePath)) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
        openFileInTab(path);
    }

    private void openFileInTab(Path filePath) {
        // Resolve relative paths relative to the project root
        Path absolutePath = filePath.isAbsolute() ? filePath : rootPath.resolve(filePath);
        
        String fileName = absolutePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = (dot > 0) ? fileName.substring(dot + 1) : "";
        try {
            FileManager fileManager = new FileManager();
            FileManager.FileContent fileContent = fileManager.loadFile(absolutePath);
            EditorTab tab = new EditorTab(fileName, fileContent.getContent(), ext, absolutePath, editorCtx);
            tab.initializeWithFileContent(fileContent);
            var img = fileTypes.iconFor(fileTypes.forExtension(ext));
            if (img != null) tab.setGraphic(new ImageView(img));
            tab.setContextMenu(buildTabContextMenu(tab));
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            docManager.didOpen(absolutePath, fileContent.getContent(), Languages.forExtension(ext).languageId());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openDiffTab(Path filePath) {
        // If an EditorTab or DiffEditorTab for this file exists, focus it
        for (var tab : tabPane.getTabs()) {
            if (tab instanceof DiffEditorTab det && filePath.equals(det.filePath)) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
            if (tab instanceof EditorTab et && filePath.equals(et.filePath)) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
        DiffEditorTab diffTab = new DiffEditorTab(filePath, editorCtx, gitManager);
        tabPane.getTabs().add(diffTab);
        tabPane.getSelectionModel().select(diffTab);
    }

    private ContextMenu buildTabContextMenu(Tab tab) {
        MenuItem closeItem = new MenuItem("Close");
        closeItem.setOnAction(e -> TabOperations.close(tabPane.getTabs(), tab));

        MenuItem closeOthersItem = new MenuItem("Close Others");
        closeOthersItem.setOnAction(e -> TabOperations.closeOthers(tabPane.getTabs(), tab));

        return new ContextMenu(closeItem, closeOthersItem);
    }

    private void saveCurrentTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et) saveTab(et);
    }

    private void saveCurrentTabAs() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et) saveTabAs(et);
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
            refreshStatus();
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
        java.io.File file = chooser.showSaveDialog(primaryStage);
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
                leftPanel.refreshTree();
                refreshStatus();
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Failed to save: " + e.getMessage()).showAndWait();
            }
        }
    }

    private void handleFileRenamed(Path oldPath, Path newPath) {
        for (Tab t : tabPane.getTabs()) {
            if (t instanceof EditorTab et && oldPath.equals(et.filePath)) {
                et.filePath = newPath;
                et.baseTitle = newPath.getFileName().toString();
                et.setModified(et.modified);
            }
        }
        refreshStatus();
    }

    private void handleFileDeleted(Path path) {
        tabPane.getTabs().removeIf(t -> t instanceof EditorTab et && path.equals(et.filePath));
        refreshStatus();
    }

    private void handleFileDiscarded(Path path) {
        if (!Files.exists(path)) {
            handleFileDeleted(path);
        } else {
            for (Tab t : tabPane.getTabs()) {
                if (t instanceof EditorTab et && path.equals(et.filePath)) {
                    reloadTabFromDisk(et);
                    break;
                }
            }
            refreshStatus();
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

    private void updateStylesheet(String extension) {
        Scene scene = tabPane.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            String sheet = Languages.forExtension(extension).stylesheetResource();
            scene.getStylesheets().add(Main.class.getResource(sheet).toExternalForm());
            scene.getStylesheets().add(Main.class.getResource("/editor/keywords/lsp.css").toExternalForm());
        }
    }

    private void updateViewMenu(EditorTab et) {
        if (viewEditorItem == null) return;
        boolean hasPreview = previewRegistry.hasPreview(et.extension);
        viewSplitItem.setDisable(!hasPreview);
        viewPreviewItem.setDisable(!hasPreview);
        switch (et.currentViewMode) {
            case EDITOR -> viewEditorItem.setSelected(true);
            case SPLIT -> viewSplitItem.setSelected(true);
            case PREVIEW -> viewPreviewItem.setSelected(true);
        }
    }

    private void refreshStatus() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof EditorTab et && et.filePath != null) {
            setStatus(et.filePath);
            refreshBranchButton();
            return;
        }
        leftPanel.getSelectedFilePath().ifPresentOrElse(this::setStatus, this::clearStatus);
        refreshBranchButton();
    }
    
    private void refreshBranchButton() {
        if (branchButton != null) {
            branchButton.refresh();
        }
    }

    private void setStatus(Path path) {
        String type = fileTypes.forPath(path).name();
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            size = -1;
        }
        statusTypeLabel.setText(type);
        statusSizeLabel.setText(size >= 0 ? humanReadableSize(size) : "");
    }

    private void clearStatus() {
        statusTypeLabel.setText("");
        statusSizeLabel.setText("");
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }


    @Override
    public void stop() {
        System.err.println("[Main] Application stop() called, initiating graceful shutdown...");

        for (Tab t : tabPane.getTabs()) {
            if (t instanceof EditorTab et) {
                et.dispose();
            }
        }

        try {
            System.err.println("[Main] Waiting for LSP servers to shutdown...");
            lspRegistry.shutdownAll().get(10, java.util.concurrent.TimeUnit.SECONDS);
            System.err.println("[Main] LSP servers shutdown complete");
        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println("[Main] LSP server shutdown timed out after 10 seconds");
        } catch (Exception e) {
            System.err.println("[Main] Error during LSP shutdown: " + e.getMessage());
        }

        System.err.println("[Main] Closing git manager and file watcher...");
        if (gitManager != null) {
            gitManager.close();
        }

        System.err.println("[Main] Shutting down executor service...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[Main] Executor did not terminate within 5 seconds, forcing shutdown...");
                executor.shutdownNow();
                executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
            }
            System.err.println("[Main] Executor shutdown complete");
        } catch (InterruptedException e) {
            System.err.println("[Main] Interrupted while waiting for executor shutdown");
            executor.shutdownNow();
        }

        System.err.println("[Main] Graceful shutdown complete, exiting JVM...");
        System.exit(0);
    }
}
