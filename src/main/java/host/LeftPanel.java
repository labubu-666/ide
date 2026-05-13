package host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import host.plugins.versioncontrol.git.SourceControlPanel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import managers.GitManager;
import filetype.FileTypeRegistry;
import search.SearchMatch;
import search.SearchService;

class LeftPanel extends HBox {

    private record TreeNode(String name, Path path) {
        @Override
        public String toString() { return name; }
    }

    private static final double EXPANDED_DIVIDER = 0.25;
    private static final double COLLAPSED_DIVIDER = 0.025;
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(180);

    private final FileTypeRegistry fileTypes;
    private final EditorNavigator navigator;
    private final Consumer<Path> onOpenDiff;
    private final BiConsumer<Path, Path> onFileRenamed;
    private final Consumer<Path> onFileDeleted;
    private final Consumer<Path> onFileDiscarded;
    private final Consumer<Double> onDividerChange;
    private final GitManager gitManager;

    private final TreeView<TreeNode> fileTree;
    private final TreeItem<TreeNode> rootItem;
    private final Path rootPath;
    private final StackPane contentArea;
    private final VBox searchContent;
    private final TextField searchQueryField;
    private final ListView<SearchMatch> searchResultList;
    private final TextFlow searchStatusFlow;
    private final GlobalSearchController searchController;
    private final PauseTransition searchDebounce;

    private final Button fileBrowserButton;
    private final Button sourceControlButton;
    private final Button storeButton;
    private final Button searchButton;

    private SourceControlPanel sourceControlPanel;
    private Thread searchThread;
    private boolean expanded = true;
    private String selectedTab = "file-browser";

    LeftPanel(Path rootPath, FileTypeRegistry fileTypes,
              EditorNavigator navigator,
              Consumer<Path> onOpenDiff,
              BiConsumer<Path, Path> onFileRenamed,
              Consumer<Path> onFileDeleted,
              Consumer<Path> onFileDiscarded,
              Consumer<Double> onDividerChange,
              GitManager gitManager) {
        this.rootPath = rootPath;
        this.fileTypes = fileTypes;
        this.navigator = navigator;
        this.onFileRenamed = onFileRenamed;
        this.onFileDeleted = onFileDeleted;
        this.onFileDiscarded = onFileDiscarded;
        this.onDividerChange = onDividerChange;
        this.onOpenDiff = onOpenDiff;
        this.gitManager = gitManager != null ? gitManager : new GitManager(rootPath);
        this.searchController = new GlobalSearchController(new SearchService(rootPath));

        searchQueryField = new TextField();
        searchQueryField.setPromptText("Search in files...");

        searchResultList = new ListView<>();
        searchResultList.setCellFactory(lv -> new SearchMatchCell(rootPath));

        searchStatusFlow = new TextFlow();

        searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
        searchDebounce.setOnFinished(e -> performSearch(searchQueryField.getText()));

        searchContent = new VBox(8, searchQueryField, searchResultList, searchStatusFlow);
        searchContent.setPadding(new Insets(12));
        VBox.setVgrow(searchResultList, Priority.ALWAYS);

        searchQueryField.textProperty().addListener((obs, oldV, newV) -> scheduleSearch(newV));
        searchQueryField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                if (!searchResultList.getItems().isEmpty()) {
                    searchResultList.getSelectionModel().selectFirst();
                    searchResultList.requestFocus();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (searchResultList.getSelectionModel().getSelectedItem() == null
                        && !searchResultList.getItems().isEmpty()) {
                    searchResultList.getSelectionModel().selectFirst();
                }
                openSelectedSearchResult();
            }
        });

        searchResultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelectedSearchResult();
            }
        });

        searchResultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelectedSearchResult();
        });

        VBox sidebar = new VBox();
        sidebar.setPrefWidth(24);
        sidebar.setMinWidth(24);
        sidebar.setMaxWidth(24);
        sidebar.setSpacing(4);

        fileBrowserButton = createTabButton("File Browser", "/host/icons/document.png");
        sourceControlButton = createTabButton("Source Control", "/host/icons/arrow-split-090.png");
        storeButton = createTabButton("Store", "/host/icons/store.png");
        searchButton = createTabButton("Search", "/host/icons/magnifier.png");

        fileBrowserButton.setOnAction(e -> selectTab("file-browser"));
        sourceControlButton.setOnAction(e -> selectTab("source-control"));
        storeButton.setOnAction(e -> selectTab("store"));
        searchButton.setOnAction(e -> selectTab("search"));
        fileBrowserButton.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        sidebar.getChildren().addAll(fileBrowserButton, sourceControlButton, storeButton, searchButton);

        String label = rootPath.getFileName() != null ? rootPath.getFileName().toString() : rootPath.toString();
        rootItem = new TreeItem<>(new TreeNode(label, rootPath));
        rootItem.setExpanded(true);
        fileTree = new TreeView<>(rootItem);
        fileTree.setPrefWidth(220);
        fileTree.setMinWidth(150);
        fileTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(TreeNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.name());
                Path p = item.path();
                if (p != null && Files.isRegularFile(p)) {
                    var img = fileTypes.iconFor(fileTypes.forPath(p));
                    setGraphic(img != null ? new ImageView(img) : null);
                } else if (p != null && Files.isDirectory(p)) {
                    var img = fileTypes.folderIcon();
                    setGraphic(img != null ? new ImageView(img) : null);
                } else {
                    setGraphic(null);
                }
            }
        });

        if (Files.exists(rootPath)) {
            try {
                populateTree(rootItem, rootPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ContextMenu treeContextMenu = new ContextMenu();
        MenuItem ctxNewFile = new MenuItem("New File...");
        ctxNewFile.setOnAction(e -> {
            TreeItem<TreeNode> sel = fileTree.getSelectionModel().getSelectedItem();
            if (sel == null) sel = rootItem;
            Path dir = sel.getValue().path();
            if (Files.isRegularFile(dir)) dir = dir.getParent();
            createFileInTree(dir);
        });
        MenuItem ctxNewFolder = new MenuItem("New Folder...");
        ctxNewFolder.setOnAction(e -> {
            TreeItem<TreeNode> sel = fileTree.getSelectionModel().getSelectedItem();
            if (sel == null) sel = rootItem;
            Path dir = sel.getValue().path();
            if (Files.isRegularFile(dir)) dir = dir.getParent();
            createFolderInTree(dir);
        });
        MenuItem ctxRename = new MenuItem("Rename...");
        ctxRename.setOnAction(e -> {
            TreeItem<TreeNode> sel = fileTree.getSelectionModel().getSelectedItem();
            if (sel != null && sel != rootItem) renameInTree(sel);
        });
        MenuItem ctxDelete = new MenuItem("Delete");
        ctxDelete.setOnAction(e -> {
            TreeItem<TreeNode> sel = fileTree.getSelectionModel().getSelectedItem();
            if (sel != null && sel != rootItem) deleteFromTree(sel);
        });
        treeContextMenu.getItems().addAll(ctxNewFile, ctxNewFolder, new SeparatorMenuItem(), ctxRename, ctxDelete);
        fileTree.setContextMenu(treeContextMenu);

        fileTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                Path filePath = newVal.getValue().path();
                if (filePath != null && Files.isRegularFile(filePath)) {
                    navigator.openFile(filePath);
                }
            }
        });

        // double-click in tree opens diff if source control tab is active
        fileTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<TreeNode> sel = fileTree.getSelectionModel().getSelectedItem();
                if (sel != null && sel.isLeaf()) {
                    Path p = sel.getValue().path();
                    if (p != null && Files.isRegularFile(p) && onOpenDiff != null) {
                        onOpenDiff.accept(p);
                    }
                }
            }
        });

        contentArea = new StackPane();
        contentArea.getChildren().add(fileTree);

        getChildren().addAll(sidebar, contentArea);
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        setSpacing(0);
    }

    Optional<Path> getSelectedFilePath() {
        TreeItem<TreeNode> item = fileTree.getSelectionModel().getSelectedItem();
        if (item != null) {
            Path p = item.getValue().path();
            if (p != null && Files.isRegularFile(p)) return Optional.of(p);
        }
        return Optional.empty();
    }

    void refreshTree() {
        rootItem.getChildren().clear();
        if (Files.exists(rootPath)) {
            try {
                populateTree(rootItem, rootPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void showSearchPanel() {
        if (expanded && "search".equals(selectedTab)) {
            Platform.runLater(searchQueryField::requestFocus);
            return;
        }
        selectTab("search");
        Platform.runLater(searchQueryField::requestFocus);
    }

    private void selectTab(String tabId) {
        boolean wasExpanded = expanded;
        if (selectedTab.equals(tabId)) {
            expanded = !expanded;
        } else {
            expanded = true;
        }
        selectedTab = tabId;

        if (wasExpanded != expanded) {
            onDividerChange.accept(expanded ? EXPANDED_DIVIDER : COLLAPSED_DIVIDER);
        }

        String baseStyle = "-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
        fileBrowserButton.setStyle(baseStyle);
        sourceControlButton.setStyle(baseStyle);
        storeButton.setStyle(baseStyle);
        searchButton.setStyle(baseStyle);

        contentArea.setVisible(expanded);
        contentArea.setManaged(expanded);

        if (!expanded) {
            contentArea.getChildren().clear();
            return;
        }

        contentArea.getChildren().clear();
        switch (tabId) {
            case "file-browser" -> contentArea.getChildren().add(fileTree);
            case "source-control" -> {
                if (sourceControlPanel == null) {
                    sourceControlPanel = new SourceControlPanel(gitManager, v -> {
                        // Refresh callback - can be used to refresh other UI if needed
                    }, p -> {
                        // open diff callback forwarded from Main
                        if (onOpenDiff != null) onOpenDiff.accept(p);
                    }, p -> {
                        refreshTree();
                        if (onFileDiscarded != null) onFileDiscarded.accept(p);
                    });
                } else {
                    sourceControlPanel.refreshStatus();
                }
                contentArea.getChildren().add(sourceControlPanel);
            }
            case "store" -> {
                Label lbl = new Label("Store - Coming Soon");
                lbl.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
                contentArea.getChildren().add(lbl);
            }
            case "search" -> contentArea.getChildren().add(searchContent);
        }

        if (!"search".equals(tabId)) {
            searchDebounce.stop();
            interruptSearchThread();
        }
    }

    private void scheduleSearch(String query) {
        searchDebounce.stop();
        interruptSearchThread();
        if (!searchController.prepareQuery(query)) {
            searchResultList.getItems().clear();
            searchStatusFlow.getChildren().clear();
            return;
        }
        setSearchingStatus(query);
        searchDebounce.playFromStart();
    }

    private void performSearch(String query) {
        if (query == null || query.isEmpty()) return;
        GlobalSearchController.SearchRun run = searchController.beginSearch(query);
        Thread t = new Thread(() -> runSearch(run), "global-search-panel");
        t.setDaemon(true);
        searchThread = t;
        t.start();
    }

    private void runSearch(GlobalSearchController.SearchRun run) {
        GlobalSearchController.SearchResult result = searchController.executeSearch(run);
        if (result.ignored()) return;

        Platform.runLater(() -> {
            if (!searchController.shouldApply(run, searchQueryField.getText())) return;
            if (result.hasError()) {
                setSearchStatusText(result.errorText());
                return;
            }
            searchResultList.getItems().setAll(result.results());
            setSearchStatusText(result.statusText());
        });
    }

    private void interruptSearchThread() {
        if (searchThread != null && searchThread.isAlive()) {
            searchThread.interrupt();
        }
    }

    private void setSearchingStatus(String query) {
        Text prefix = new Text("Searching for ");
        Text term = new Text(query);
        term.setStyle("-fx-font-weight: bold;");
        searchStatusFlow.getChildren().setAll(prefix, term);
    }

    private void setSearchStatusText(String status) {
        searchStatusFlow.getChildren().setAll(new Text(status));
    }

    private void openSelectedSearchResult() {
        SearchMatch selected = searchResultList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigator.openSearchMatch(selected);
        }
    }

    private void createFileInTree(Path dir) {
        TextInputDialog dlg = new TextInputDialog("Untitled");
        dlg.setTitle("New File");
        dlg.setHeaderText("File name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            Path newFile = dir.resolve(name);
            try {
                Files.createDirectories(newFile.getParent());
                Files.createFile(newFile);
                refreshTree();
                navigator.openFile(newFile);
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Could not create file: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void createFolderInTree(Path dir) {
        TextInputDialog dlg = new TextInputDialog("NewFolder");
        dlg.setTitle("New Folder");
        dlg.setHeaderText("Folder name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            try {
                Files.createDirectories(dir.resolve(name));
                refreshTree();
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Could not create folder: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void renameInTree(TreeItem<TreeNode> item) {
        Path oldPath = item.getValue().path();
        TextInputDialog dlg = new TextInputDialog(oldPath.getFileName().toString());
        dlg.setTitle("Rename");
        dlg.setHeaderText("New name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            Path newPath = oldPath.getParent().resolve(name);
            try {
                Files.move(oldPath, newPath);
                onFileRenamed.accept(oldPath, newPath);
                refreshTree();
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Could not rename: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void deleteFromTree(TreeItem<TreeNode> item) {
        Path path = item.getValue().path();
        Alert confirm = new Alert(AlertType.CONFIRMATION,
                "Delete \"" + path.getFileName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                if (Files.isDirectory(path)) {
                    deleteRecursively(path);
                } else {
                    Files.delete(path);
                }
                onFileDeleted.accept(path);
                refreshTree();
            } catch (IOException e) {
                new Alert(AlertType.ERROR, "Could not delete: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.collect(Collectors.toList())) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    private void populateTree(TreeItem<TreeNode> parent, Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            var comparator = Comparator.comparing((Path p) -> !Files.isDirectory(p))
                                       .thenComparing(Comparator.naturalOrder());
            for (Path path : stream.sorted(comparator).collect(Collectors.toList())) {
                TreeItem<TreeNode> item = new TreeItem<>(new TreeNode(path.getFileName().toString(), path));
                parent.getChildren().add(item);
                if (Files.isDirectory(path)) {
                    populateTree(item, path);
                }
            }
        }
    }

    private Button createTabButton(String tooltipText, String iconPath) {
        Button btn = new Button();
        btn.setPrefSize(24, 24);
        btn.setMinSize(24, 24);
        btn.setMaxSize(24, 24);
        btn.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        try {
            Image icon = new Image(LeftPanel.class.getResourceAsStream(iconPath));
            ImageView iconView = new ImageView(icon);
            iconView.setFitWidth(16);
            iconView.setFitHeight(16);
            btn.setGraphic(iconView);
        } catch (Exception e) {
            btn.setText("?");
        }

        Tooltip.install(btn, new Tooltip(tooltipText));
        return btn;
    }

    private static class SearchMatchCell extends ListCell<SearchMatch> {
        private static final int MATCH_CONTEXT_CHARS = 40;

        private final Path rootPath;

        SearchMatchCell(Path rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        protected void updateItem(SearchMatch match, boolean empty) {
            super.updateItem(match, empty);
            if (empty || match == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String line = match.lineText();
            int snippetStart = Math.max(0, match.columnStart() - MATCH_CONTEXT_CHARS);
            int snippetEnd = Math.min(line.length(), match.columnEnd() + MATCH_CONTEXT_CHARS);

            String preText = line.substring(snippetStart, match.columnStart());
            String matchText = line.substring(match.columnStart(), match.columnEnd());
            String postText = line.substring(match.columnEnd(), snippetEnd);

            if (snippetStart > 0) {
                preText = "..." + preText;
            }
            if (snippetEnd < line.length()) {
                postText = postText + "...";
            }

            Text pre = new Text(preText);
            Text found = new Text(matchText);
            found.setStyle("-fx-font-weight: bold; -fx-fill: #e07b00;");
            Text post = new Text(postText);

            Path relativePath;
            try {
                relativePath = rootPath.relativize(match.filePath());
            } catch (IllegalArgumentException e) {
                relativePath = match.filePath();
            }

            Text location = new Text(relativePath + ":" + match.lineNumber());
            location.setStyle("-fx-fill: #888888;");

            setText(null);
            setGraphic(new VBox(2, new TextFlow(pre, found, post), new TextFlow(location)));
        }
    }
}
