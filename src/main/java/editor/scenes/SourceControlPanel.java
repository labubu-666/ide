package editor.scenes;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import editor.GitManager;

/**
 * Source Control Panel component for managing git changes, staging, and commits.
 * Displays file changes with status indicators and provides UI for staging, unstaging,
 * and committing changes.
 */
class SourceControlPanel extends VBox {

    private final GitManager gitManager;
    private final Consumer<Void> onRefresh;
    private ListView<FileChange> changesView;
    private ListView<FileChange> stagedView;
    private TextArea messageArea;
    private Button commitButton;
    private Button amendButton;

    // File change record for display
    private record FileChange(Path path, String status, boolean staged) {
        @Override
        public String toString() {
            return status + " " + path.getFileName();
        }
    }

    SourceControlPanel(GitManager gitManager, Consumer<Void> onRefresh) {
        this.gitManager = gitManager;
        this.onRefresh = onRefresh;

        setSpacing(8);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #ffffff;");

        // Initialize fields to null - will be properly initialized if this is a git repo
        this.changesView = null;
        this.stagedView = null;
        this.messageArea = null;
        this.commitButton = null;
        this.amendButton = null;

        // Check if git repo
        if (!gitManager.isGitRepository()) {
            Label notGit = new Label("Not a git repository");
            notGit.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
            getChildren().add(notGit);
            return;
        }

        // Unstaged changes section
        Label unstaged = new Label("Changes");
        unstaged.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        changesView = new ListView<>();
        changesView.setPrefHeight(150);
        changesView.setCellFactory(lv -> createFileCell(false));

        // Staged changes section
        Label staged = new Label("Staged Changes");
        staged.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        stagedView = new ListView<>();
        stagedView.setPrefHeight(150);
        stagedView.setCellFactory(lv -> createFileCell(true));

        // Commit message section
        Label messageLabel = new Label("Commit Message");
        messageLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        messageArea = new TextArea();
        messageArea.setWrapText(true);
        messageArea.setPrefHeight(80);
        messageArea.setStyle("-fx-control-inner-background: #f5f5f5; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        messageArea.setPromptText("Describe your changes...");

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        refreshButton.setOnAction(e -> refreshStatus());

        Button stageAllButton = new Button("Stage All");
        stageAllButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        stageAllButton.setOnAction(e -> stageAll());

        Button unstageAllButton = new Button("Unstage All");
        unstageAllButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        unstageAllButton.setOnAction(e -> unstageAll());

        commitButton = new Button("Commit");
        commitButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px; -fx-text-fill: #ffffff; -fx-background-color: #007acc;");
        commitButton.setOnAction(e -> commit());

        amendButton = new Button("Amend");
        amendButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        amendButton.setOnAction(e -> amend());

        buttonBox.getChildren().addAll(refreshButton, stageAllButton, unstageAllButton, commitButton, amendButton);

        getChildren().addAll(
            unstaged, changesView,
            staged, stagedView,
            messageLabel, messageArea,
            buttonBox
        );

        VBox.setVgrow(changesView, Priority.SOMETIMES);
        VBox.setVgrow(stagedView, Priority.SOMETIMES);
        VBox.setVgrow(messageArea, Priority.SOMETIMES);

        // Initial load
        refreshStatus();
    }

    /**
     * Refresh the file status from git.
     */
    public void refreshStatus() {
        Map<Path, String> unstaged = gitManager.getUnstagedChanges();
        Map<Path, String> staged = gitManager.getStagedChanges();

        changesView.getItems().clear();
        for (Map.Entry<Path, String> entry : unstaged.entrySet()) {
            changesView.getItems().add(new FileChange(entry.getKey(), entry.getValue(), false));
        }

        stagedView.getItems().clear();
        for (Map.Entry<Path, String> entry : staged.entrySet()) {
            stagedView.getItems().add(new FileChange(entry.getKey(), entry.getValue(), true));
        }
    }

    /**
     * Create a custom cell renderer for file changes with status indicators and context menu.
     */
    private ListCell<FileChange> createFileCell(boolean isStaged) {
        return new ListCell<FileChange>() {
            @Override
            protected void updateItem(FileChange item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }

                // Create display with status indicator
                HBox box = new HBox(8);
                box.setPadding(new Insets(4, 8, 4, 8));

                Label statusLabel = new Label(item.status);
                statusLabel.setPrefWidth(30);
                statusLabel.setStyle(getStatusStyle(item.status));

                Label fileLabel = new Label(item.path.getFileName().toString());
                fileLabel.setStyle("-fx-text-fill: #333;");

                Path parent = item.path.getParent();
                Label pathLabel = new Label(parent != null && parent.getFileName() != null
                    ? parent.getFileName().toString()
                    : "(root)");
                pathLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");

                VBox details = new VBox(0);
                details.getChildren().addAll(fileLabel, pathLabel);

                box.getChildren().addAll(statusLabel, details);
                HBox.setHgrow(details, Priority.ALWAYS);
                setGraphic(box);

                // Context menu for individual file actions
                ContextMenu menu = new ContextMenu();
                if (isStaged) {
                    MenuItem unstageItem = new MenuItem("Unstage");
                    unstageItem.setOnAction(e -> {
                        gitManager.unstageFile(item.path);
                        refreshStatus();
                        if (onRefresh != null) onRefresh.accept(null);
                    });
                    menu.getItems().add(unstageItem);
                } else {
                    MenuItem stageItem = new MenuItem("Stage");
                    stageItem.setOnAction(e -> {
                        gitManager.stageFile(item.path);
                        refreshStatus();
                        if (onRefresh != null) onRefresh.accept(null);
                    });
                    menu.getItems().add(stageItem);
                }
                setContextMenu(menu);
            }
        };
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

    private void stageAll() {
        if (gitManager.stageAll()) {
            refreshStatus();
            if (onRefresh != null) onRefresh.accept(null);
        } else {
            new Alert(AlertType.ERROR, "Failed to stage all changes").showAndWait();
        }
    }

    private void unstageAll() {
        if (gitManager.unstageAll()) {
            refreshStatus();
            if (onRefresh != null) onRefresh.accept(null);
        } else {
            new Alert(AlertType.ERROR, "Failed to unstage all changes").showAndWait();
        }
    }

    private void commit() {
        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            new Alert(AlertType.WARNING, "Please enter a commit message").showAndWait();
            return;
        }

        if (stagedView.getItems().isEmpty()) {
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
