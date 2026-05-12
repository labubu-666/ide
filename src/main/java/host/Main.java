package host;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import managers.GitManager;
import settings.Settings;
import filetype.FileTypeRegistry;
import language.Languages;
import lsp.CompletionProvider;
import lsp.DocumentManager;
import lsp.LspServerRegistry;
import preview.PreviewRegistry;
import search.SearchService;
import settings.SettingsDialog;
import utils.FileUtils;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private CenterPanel centerPanel;
    private ExecutorService executor;
    private Stage primaryStage;
    private Path rootPath;
    private LspServerRegistry lspRegistry;
    private FileTypeRegistry fileTypes;
    private StatusBar statusBar;
    private PreviewRegistry previewRegistry;
    private RadioMenuItem viewEditorItem;
    private RadioMenuItem viewSplitItem;
    private RadioMenuItem viewPreviewItem;
    private SplitPane mainSplitPane;
    private LeftPanel leftPanel;
    private RightPanel rightPanel;
    private GitManager gitManager;
    private ComboBox<String> runConfigurationDropdown;
    private BottomPanel bottomPanel;

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
        showRunConfigurationCollisionAlertIfNeeded();
        // TODO: Temporarily synchronous due to lack of backgroiund jobs feature - will be made async later
        SearchService.initializeIndex(rootPath);
        gitManager = new GitManager(rootPath);
        lspRegistry = new LspServerRegistry(rootPath, (uri, diagnostics) ->
            Platform.runLater(() -> centerPanel.applyDiagnostics(uri, diagnostics)));
        DocumentManager docManager = new DocumentManager(lspRegistry);
        CompletionProvider completionProvider = new CompletionProvider(lspRegistry);
        fileTypes = new FileTypeRegistry();
        previewRegistry = new PreviewRegistry();

        centerPanel = new CenterPanel(rootPath, executor, docManager, completionProvider,
                previewRegistry, fileTypes, gitManager,
                this::onEditorActivated,
                this::onDiffActivated,
                this::refreshStatus,
                this::refreshTree);
        EditorNavigator navigator = centerPanel;

        MenuBar menuBar = buildMenuBar();
        ToolBar runToolbar = buildRunToolbar();

        leftPanel = new LeftPanel(rootPath, fileTypes,
                navigator,
                centerPanel::openDiff,
                centerPanel::handleFileRenamed,
                centerPanel::handleFileDeleted,
                centerPanel::handleFileDiscarded,
                pos -> mainSplitPane.getDividers().get(0).setPosition(pos),
                gitManager);

        rightPanel = new RightPanel(pos -> mainSplitPane.getDividers().get(1).setPosition(pos));

        mainSplitPane = new SplitPane(leftPanel, centerPanel, rightPanel);
        SplitPane.setResizableWithParent(leftPanel, Boolean.FALSE);
        SplitPane.setResizableWithParent(rightPanel, Boolean.FALSE);
        mainSplitPane.setDividerPositions(0.25, 1.0 - 0.025);
        mainSplitPane.setStyle("-fx-padding: 0;");

        statusBar = new StatusBar(gitManager, fileTypes);
        bottomPanel = new BottomPanel();

        VBox root = new VBox(menuBar, runToolbar, mainSplitPane, bottomPanel, statusBar);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        root.setPrefSize(1024, 768);

        Scene scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN).match(event)) {
                centerPanel.saveActive();
                event.consume();
            } else if (new KeyCodeCombination(KeyCode.F,
                    KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN).match(event)) {
                new GlobalSearchDialog(primaryStage, rootPath, navigator::openSearchMatch).show();
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
        newItem.setOnAction(e -> centerPanel.createNew());

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
        saveItem.setOnAction(e -> centerPanel.saveActive());

        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+S"));
        saveAsItem.setOnAction(e -> centerPanel.saveActiveAs());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(newItem, new SeparatorMenuItem(), saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);

        ToggleGroup viewToggle = new ToggleGroup();
        viewEditorItem = new RadioMenuItem("Editor");
        viewEditorItem.setToggleGroup(viewToggle);
        viewEditorItem.setSelected(true);
        viewEditorItem.setAccelerator(KeyCombination.keyCombination("Shortcut+1"));
        viewEditorItem.setOnAction(e -> centerPanel.setViewMode(ViewMode.EDITOR));

        viewSplitItem = new RadioMenuItem("Split");
        viewSplitItem.setToggleGroup(viewToggle);
        viewSplitItem.setAccelerator(KeyCombination.keyCombination("Shortcut+2"));
        viewSplitItem.setOnAction(e -> centerPanel.setViewMode(ViewMode.SPLIT));

        viewPreviewItem = new RadioMenuItem("Preview");
        viewPreviewItem.setToggleGroup(viewToggle);
        viewPreviewItem.setAccelerator(KeyCombination.keyCombination("Shortcut+3"));
        viewPreviewItem.setOnAction(e -> centerPanel.setViewMode(ViewMode.PREVIEW));

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(viewEditorItem, viewSplitItem, viewPreviewItem);

        MenuItem settingsItem = new MenuItem("Preferences...");
        settingsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+,"));
        settingsItem.setOnAction(e -> {
            SettingsDialog settingsDialog = new SettingsDialog(primaryStage, rootPath);
            settingsDialog.show();
            settingsDialog.getStage().setOnHiding(event -> {
                refreshRunConfigurationDropdown();
                showRunConfigurationCollisionAlertIfNeeded();
            });
        });

        Menu settingsMenu = new Menu("Settings");
        settingsMenu.getItems().add(settingsItem);

        return new MenuBar(fileMenu, viewMenu, settingsMenu);
    }

    private ToolBar buildRunToolbar() {
        runConfigurationDropdown = new ComboBox<>();
        runConfigurationDropdown.setPromptText("Run configuration");
        runConfigurationDropdown.setPrefWidth(320);

        Button runButton = new Button("Run");
        runButton.setOnAction(e -> {
            String selected = runConfigurationDropdown.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isBlank()) {
                bottomPanel.appendOutput("[run] No run configuration selected.");
                return;
            }
            bottomPanel.appendOutput("[run] Selected configuration: " + selected);
            bottomPanel.appendOutput("[run] Execution wiring is not implemented yet.");
        });

        ToolBar toolBar = new ToolBar(runConfigurationDropdown, runButton);
        refreshRunConfigurationDropdown();
        return toolBar;
    }

    private void refreshRunConfigurationDropdown() {
        if (runConfigurationDropdown == null) {
            return;
        }

        String previousSelection = runConfigurationDropdown.getSelectionModel().getSelectedItem();
        runConfigurationDropdown.getItems().setAll(Settings.getRunConfigurations().keySet());

        if (previousSelection != null && runConfigurationDropdown.getItems().contains(previousSelection)) {
            runConfigurationDropdown.getSelectionModel().select(previousSelection);
            return;
        }

        if (!runConfigurationDropdown.getItems().isEmpty()) {
            runConfigurationDropdown.getSelectionModel().selectFirst();
        }
    }

    private void showRunConfigurationCollisionAlertIfNeeded() {
        Set<String> collisions = Settings.getRunConfigurationCollisionKeys();
        if (collisions.isEmpty()) {
            return;
        }

        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Run Configuration Collision");
        alert.setHeaderText("Project run configurations override global ones");
        alert.setContentText("Colliding keys: " + String.join(", ", collisions));
        alert.showAndWait();
    }

    // --- CenterPanel callbacks ---

    private void onEditorActivated(ActiveEditorInfo info) {
        updateStylesheet(info.extension());
        updateViewMenu(info.hasPreview(), info.viewMode());
    }

    private void onDiffActivated() {
        Scene scene = primaryStage.getScene();
        if (scene == null) return;

        scene.getStylesheets().clear();

        String extension = "";
        var selected = centerPanel.getSelectionModel().getSelectedItem();
        if (selected instanceof DiffEditorTab det && det.filePath != null) {
            String name = det.filePath.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) extension = name.substring(dot + 1);
        }

        String sheet = Languages.forExtension(extension).stylesheetResource();
        addStylesheet(scene, sheet);
        addStylesheet(scene, "/host/keywords/lsp.css");
        addStylesheet(scene, "/host/keywords/diff.css");
    }

    private void refreshStatus() {
        centerPanel.getActiveFilePath().ifPresentOrElse(
                statusBar::update,
                () -> leftPanel.getSelectedFilePath().ifPresentOrElse(statusBar::update, statusBar::clear));
    }

    private void refreshTree() {
        leftPanel.refreshTree();
    }

    // --- Internal ---

    private void updateStylesheet(String extension) {
        Scene scene = primaryStage.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            String sheet = Languages.forExtension(extension).stylesheetResource();
            addStylesheet(scene, sheet);
            addStylesheet(scene, "/host/keywords/lsp.css");
        }
    }

    private void addStylesheet(Scene scene, String resourcePath) {
        if (resourcePath == null) return;
        var resource = Main.class.getResource(resourcePath);
        if (resource == null) return;
        String url = resource.toExternalForm();
        if (!scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }

    private void updateViewMenu(boolean hasPreview, ViewMode viewMode) {
        if (viewEditorItem == null) return;
        viewSplitItem.setDisable(!hasPreview);
        viewPreviewItem.setDisable(!hasPreview);
        switch (viewMode) {
            case EDITOR -> viewEditorItem.setSelected(true);
            case SPLIT -> viewSplitItem.setSelected(true);
            case PREVIEW -> viewPreviewItem.setSelected(true);
        }
    }

    @Override
    public void stop() {
        System.err.println("[Main] Application stop() called, initiating graceful shutdown...");

        centerPanel.disposeAll();

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
