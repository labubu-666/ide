package editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import editor.plugins.versioncontrol.git.SourceControlPanel;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import managers.GitManager;
import filetype.FileTypeRegistry;

class LeftPanel extends HBox {

    private record TreeNode(String name, Path path) {
        @Override
        public String toString() { return name; }
    }

    private static final double EXPANDED_DIVIDER = 0.25;
    private static final double COLLAPSED_DIVIDER = 0.025;

    private final FileTypeRegistry fileTypes;
    private final Consumer<Path> onFileOpen;
    private final Consumer<Path> onOpenDiff;
    private final BiConsumer<Path, Path> onFileRenamed;
    private final Consumer<Path> onFileDeleted;
    private final Consumer<Double> onDividerChange;
    private final GitManager gitManager;

    private final TreeView<TreeNode> fileTree;
    private final TreeItem<TreeNode> rootItem;
    private final Path rootPath;
    private final StackPane contentArea;

    private final Button fileBrowserButton;
    private final Button sourceControlButton;
    private final Button storeButton;

    private SourceControlPanel sourceControlPanel;
    private boolean expanded = true;
    private String selectedTab = "file-browser";

    LeftPanel(Path rootPath, FileTypeRegistry fileTypes,
              Consumer<Path> onFileOpen,
              Consumer<Path> onOpenDiff,
              BiConsumer<Path, Path> onFileRenamed,
              Consumer<Path> onFileDeleted,
              Consumer<Double> onDividerChange,
              GitManager gitManager) {
        this.rootPath = rootPath;
        this.fileTypes = fileTypes;
        this.onFileOpen = onFileOpen;
        this.onFileRenamed = onFileRenamed;
        this.onFileDeleted = onFileDeleted;
        this.onDividerChange = onDividerChange;
        this.onOpenDiff = onOpenDiff;
        this.gitManager = gitManager != null ? gitManager : new GitManager(rootPath);

        VBox sidebar = new VBox();
        sidebar.setPrefWidth(24);
        sidebar.setMinWidth(24);
        sidebar.setSpacing(4);

        fileBrowserButton = createTabButton("File Browser", "/editor/icons/document.png");
        sourceControlButton = createTabButton("Source Control", "/editor/icons/arrow-split-090.png");
        storeButton = createTabButton("Store", "/editor/icons/store.png");

        fileBrowserButton.setOnAction(e -> selectTab("file-browser"));
        sourceControlButton.setOnAction(e -> selectTab("source-control"));
        storeButton.setOnAction(e -> selectTab("store"));
        fileBrowserButton.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        sidebar.getChildren().addAll(fileBrowserButton, sourceControlButton, storeButton);

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
                    onFileOpen.accept(filePath);
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

    private void selectTab(String tabId) {
        if (selectedTab.equals(tabId)) {
            expanded = !expanded;
        } else {
            expanded = true;
        }
        selectedTab = tabId;

        onDividerChange.accept(expanded ? EXPANDED_DIVIDER : COLLAPSED_DIVIDER);

        String baseStyle = "-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
        fileBrowserButton.setStyle(baseStyle);
        sourceControlButton.setStyle(baseStyle);
        storeButton.setStyle(baseStyle);

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
                onFileOpen.accept(newFile);
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
}
