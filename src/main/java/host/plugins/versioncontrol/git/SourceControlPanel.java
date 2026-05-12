package host.plugins.versioncontrol.git;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.geometry.Pos;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import managers.GitManager;

import javafx.scene.control.ScrollPane;

public class SourceControlPanel extends ScrollPane {

    /**
     * Header component for the SourceControlPanel.
     * Displays the title and a refresh button, and allows listening for refresh callbacks.
     */
    public static class Header extends HBox {
        private final Button refreshButton;
        private final Label titleLabel;
        private java.util.List<Consumer<Void>> refreshListeners = new java.util.ArrayList<>();

        public Header() {
            super(8);
            setAlignment(Pos.CENTER_LEFT);
            setId("sourceControlHeader");

            titleLabel = new Label("Source Control");
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            titleLabel.setId("headerTitle");

            refreshButton = new Button("Refresh");
            refreshButton.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 11px;");
            refreshButton.setId("refreshButton");
            refreshButton.setOnAction(e -> {
                refreshListeners.forEach(listener -> listener.accept(null));
            });

            HBox.setHgrow(refreshButton, Priority.ALWAYS);
            getChildren().addAll(titleLabel, refreshButton);
        }

        /**
         * Add a listener to be notified when the refresh button is clicked.
         * @param listener the listener to add
         */
        public void addRefreshListener(Consumer<Void> listener) {
            refreshListeners.add(listener);
        }

        /**
         * Remove a refresh listener.
         * @param listener the listener to remove
         */
        public void removeRefreshListener(Consumer<Void> listener) {
            refreshListeners.remove(listener);
        }

        /**
         * Get the refresh button for testing purposes.
         * @return the refresh button
         */
        public Button getRefreshButton() {
            return refreshButton;
        }

        /**
         * Get the title label for testing purposes.
         * @return the title label
         */
        public Label getTitleLabel() {
            return titleLabel;
        }
    }

    private final GitManager gitManager;
    private final Consumer<Void> onRefresh;
    private final Consumer<Path> onOpenDiff;
    private final Consumer<Path> onFileDiscarded;
    private TreeView<ChangeTreeNode> changesTree;
    private TreeView<ChangeTreeNode> stagedTree;
    private TitledPane unstagedPane;
    private TitledPane stagedPane;
    private TextArea messageArea;
    private Button commitButton;
    private Button amendButton;

    // File change record for display
    public record FileChange(Path path, String status, boolean staged) {
        @Override
        public String toString() {
            return status + " " + path.getFileName();
        }
    }

    private record ChangeTreeNode(String name, boolean folder, FileChange change) {
    }

    public SourceControlPanel(GitManager gitManager, Consumer<Void> onRefresh, Consumer<Path> onOpenDiff, Consumer<Path> onFileDiscarded) {
        this.gitManager = gitManager;
        this.onRefresh = onRefresh;
        this.onOpenDiff = onOpenDiff;
        this.onFileDiscarded = onFileDiscarded;

        VBox content = new VBox(8);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: #ffffff;");

        // Initialize fields to null - will be properly initialized if this is a git repo
        this.changesTree = null;
        this.stagedTree = null;
        this.messageArea = null;
        this.commitButton = null;
        this.amendButton = null;

        // Check if git repo
        if (!gitManager.isGitRepository()) {
            Label notGit = new Label("Not a git repository");
            notGit.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
            content.getChildren().add(notGit);
            setContent(content);
            setFitToWidth(true);
            return;
        }
        
        // Subscribe to file system changes for auto-refresh
        subscribeToGitChanges();

        // Header
        Header header = new Header();
        header.addRefreshListener(v -> refreshStatus());

        // Commit message section
        Label messageLabel = new Label("Commit Message");
        messageLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        messageArea = new TextArea();
        messageArea.setWrapText(true);
        messageArea.setPrefHeight(80);
        messageArea.setStyle("-fx-control-inner-background: #f5f5f5; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        messageArea.setPromptText("Describe your changes...");

        // Commit and Amend buttons
        HBox commitButtonBox = new HBox(8);
        commitButtonBox.setPadding(new Insets(8, 0, 0, 0));
        commitButton = new Button("Commit");
        commitButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px; -fx-text-fill: #ffffff; -fx-background-color: #007acc;");
        commitButton.setOnAction(e -> commit());
        amendButton = new Button("Amend");
        amendButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        amendButton.setOnAction(e -> amend());
        commitButtonBox.getChildren().addAll(commitButton, amendButton);

        // Unstaged changes section
        unstagedPane = new TitledPane();
        unstagedPane.setText("Changes");
        unstagedPane.setCollapsible(true);
        unstagedPane.setExpanded(true);
        changesTree = new TreeView<>();
        changesTree.setShowRoot(false);
        changesTree.setCellFactory(tv -> createTreeCell(false));
        unstagedPane.setContent(changesTree);

        // Staged changes section
        stagedPane = new TitledPane();
        stagedPane.setText("Staged Changes");
        stagedPane.setCollapsible(true);
        stagedPane.setExpanded(true);
        stagedTree = new TreeView<>();
        stagedTree.setShowRoot(false);
        stagedTree.setCellFactory(tv -> createTreeCell(true));
        stagedPane.setContent(stagedTree);

        content.getChildren().addAll(
            header,
            messageLabel, messageArea,
            commitButtonBox,
            stagedPane,
            unstagedPane
        );

        VBox.setVgrow(changesTree, Priority.SOMETIMES);
        VBox.setVgrow(stagedTree, Priority.SOMETIMES);
        VBox.setVgrow(messageArea, Priority.SOMETIMES);

        setContent(content);
        setFitToWidth(true);

        // Initial load
        refreshStatus();
    }

    /**
     * Subscribes to git file system changes to automatically refresh the panel.
     */
    private void subscribeToGitChanges() {
        managers.GitStatusWatcher watcher = gitManager.getWatcher();
        if (watcher != null) {
            watcher.addListener(changeType -> {
                // Refresh on any git change
                refreshStatus();
                if (onRefresh != null) {
                    onRefresh.accept(null);
                }
            });
        }
    }

    /**
     * Refresh the file status from git.
     */
    public void refreshStatus() {
        if (changesTree == null || stagedTree == null) {
            return;
        }

        Map<Path, String> unstaged = gitManager.getUnstagedChanges();
        Map<Path, String> staged = gitManager.getStagedChanges();

        changesTree.setRoot(buildTree(unstaged, false));
        // Hide/show unstaged changes section
        unstagedPane.setVisible(!unstaged.isEmpty());
        unstagedPane.setManaged(!unstaged.isEmpty());

        stagedTree.setRoot(buildTree(staged, true));
        // Hide/show staged changes section
        stagedPane.setVisible(!staged.isEmpty());
        stagedPane.setManaged(!staged.isEmpty());
    }

    /**
     * Create a custom cell renderer for file changes with status indicators and context menu.
     */
    private TreeCell<ChangeTreeNode> createTreeCell(boolean isStaged) {
        return new TreeCell<ChangeTreeNode>() {
            @Override
            protected void updateItem(ChangeTreeNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                if (item.folder) {
                    setText(item.name);
                    setGraphic(null);
                    return;
                }

                FileChange fileChange = item.change;

                HBox box = new HBox(8);
                box.setPadding(new Insets(4, 8, 4, 8));
                box.setAlignment(Pos.CENTER_LEFT);

                Label statusLabel = new Label(fileChange.status);
                statusLabel.setPrefWidth(30);
                statusLabel.setStyle(getStatusStyle(fileChange.status));

                Label fileLabel = new Label(fileChange.path.getFileName().toString());
                fileLabel.setStyle("-fx-text-fill: #333;");

                // Add/Remove button
                Button actionButton = new Button(isStaged ? "Remove" : "Add");
                actionButton.setStyle("-fx-padding: 2 5 2 5; -fx-font-size: 10px;");
                actionButton.setOnAction(e -> {
                    if (isStaged) {
                        gitManager.unstageFile(fileChange.path);
                    } else {
                        gitManager.stageFile(fileChange.path);
                    }
                    refreshStatus();
                    if (onRefresh != null) onRefresh.accept(null);
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox buttons = new HBox(4);
                if (!isStaged) {
                    Button discardButton = new Button("Discard");
                    discardButton.setStyle("-fx-padding: 2 5 2 5; -fx-font-size: 10px; -fx-text-fill: #f44336;");
                    discardButton.setOnAction(e -> {
                        Path absolutePath = gitManager.getRootPath().resolve(fileChange.path);
                        gitManager.discardChanges(fileChange.path);
                        refreshStatus();
                        if (onFileDiscarded != null) onFileDiscarded.accept(absolutePath);
                        if (onRefresh != null) onRefresh.accept(null);
                    });
                    buttons.getChildren().add(discardButton);
                }
                buttons.getChildren().add(actionButton);

                box.getChildren().addAll(statusLabel, fileLabel, spacer, buttons);
                setGraphic(box);
                setText(null);

                // Double-click opens diff view for this file
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && onOpenDiff != null) {
                        onOpenDiff.accept(fileChange.path);
                    }
                });
            }
        };
    }

    private TreeItem<ChangeTreeNode> buildTree(Map<Path, String> changes, boolean staged) {
        TreeItem<ChangeTreeNode> root = new TreeItem<>(new ChangeTreeNode("", true, null));
        root.setExpanded(true);

        changes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> addFileToTree(root, new FileChange(entry.getKey(), entry.getValue(), staged)));

        sortTree(root);
        return root;
    }

    private void addFileToTree(TreeItem<ChangeTreeNode> root, FileChange change) {
        TreeItem<ChangeTreeNode> current = root;
        Path path = change.path;

        for (int i = 0; i < path.getNameCount(); i++) {
            String segment = path.getName(i).toString();
            boolean isLeaf = i == path.getNameCount() - 1;

            if (isLeaf) {
                TreeItem<ChangeTreeNode> fileNode = new TreeItem<>(new ChangeTreeNode(segment, false, change));
                current.getChildren().add(fileNode);
                return;
            }

            TreeItem<ChangeTreeNode> folder = findFolderChild(current, segment);
            if (folder == null) {
                folder = new TreeItem<>(new ChangeTreeNode(segment, true, null));
                folder.setExpanded(true);
                current.getChildren().add(folder);
            }
            current = folder;
        }
    }

    private TreeItem<ChangeTreeNode> findFolderChild(TreeItem<ChangeTreeNode> parent, String name) {
        for (TreeItem<ChangeTreeNode> child : parent.getChildren()) {
            ChangeTreeNode node = child.getValue();
            if (node.folder && node.name.equals(name)) {
                return child;
            }
        }
        return null;
    }

    private void sortTree(TreeItem<ChangeTreeNode> item) {
        item.getChildren().sort((left, right) -> {
            ChangeTreeNode a = left.getValue();
            ChangeTreeNode b = right.getValue();

            if (a.folder != b.folder) {
                return a.folder ? -1 : 1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });

        for (TreeItem<ChangeTreeNode> child : item.getChildren()) {
            sortTree(child);
        }
    }

    /**
     * Get the style for a status indicator based on file status code.
     */
    private String getStatusStyle(String status) {
        return switch (status) {
            case "M" -> "-fx-text-fill: #ff9800; -fx-font-weight: bold; -fx-font-size: 11px;"; // Modified - Orange
            case "A" -> "-fx-text-fill: #4caf50; -fx-font-weight: bold; -fx-font-size: 11px;"; // Added - Green
            case "D" -> "-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 11px;"; // Deleted - Red
            case "R" -> "-fx-text-fill: #2196f3; -fx-font-weight: bold; -fx-font-size: 11px;"; // Renamed - Blue
            case "U" -> "-fx-text-fill: #999999; -fx-font-weight: bold; -fx-font-size: 11px;"; // Untracked - Gray
            case "C" -> "-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 12px;"; // Conflicting - Red bold
            default -> "-fx-text-fill: #666666; -fx-font-size: 11px;";
        };
    }

    private void commit() {
        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            new Alert(AlertType.WARNING, "Please enter a commit message").showAndWait();
            return;
        }

        if (stagedTree.getRoot() == null || stagedTree.getRoot().getChildren().isEmpty()) {
            new Alert(AlertType.WARNING, "No staged changes to commit").showAndWait();
            return;
        }

        if (gitManager.commit(message)) {
            messageArea.clear();
            refreshStatus();
            new Alert(AlertType.INFORMATION, "Changes committed successfully").showAndWait();
            if (onRefresh != null) onRefresh.accept(null);
        } else {
            new Alert(AlertType.ERROR, "Failed to commit changes").showAndWait();
        }
    }

    private void amend() {
        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            new Alert(AlertType.WARNING, "Please enter an amended commit message").showAndWait();
            return;
        }

        if (gitManager.amendCommit(message)) {
            messageArea.clear();
            refreshStatus();
            new Alert(AlertType.INFORMATION, "Commit amended successfully").showAndWait();
            if (onRefresh != null) onRefresh.accept(null);
        } else {
            new Alert(AlertType.ERROR, "Failed to amend commit").showAndWait();
        }
    }
}
