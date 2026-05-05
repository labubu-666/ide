package host.plugins.versioncontrol.git;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import managers.GitManager;

/**
 * Dialog for managing git branches.
 * Allows users to view, create, checkout, and delete branches.
 */
public class BranchesDialog {

    private final Stage stage;
    private final GitManager gitManager;
    private final TextField searchField;
    private final ListView<String> branchListView;
    private final ObservableList<String> allBranches;
    private final ObservableList<String> filteredBranches;
    private Consumer<Void> onBranchChange;

    /**
     * Creates a new BranchesDialog.
     *
     * @param owner the owner window
     * @param gitManager the GitManager instance
     */
    public BranchesDialog(Window owner, GitManager gitManager) {
        this(owner, gitManager, null);
    }

    /**
     * Creates a new BranchesDialog with a callback for branch changes.
     *
     * @param owner the owner window
     * @param gitManager the GitManager instance
     * @param onBranchChange callback invoked when branches change
     */
    public BranchesDialog(Window owner, GitManager gitManager, Consumer<Void> onBranchChange) {
        this.gitManager = gitManager;
        this.onBranchChange = onBranchChange;
        this.allBranches = FXCollections.observableArrayList();
        this.filteredBranches = FXCollections.observableArrayList();

        stage = new Stage();
        stage.setTitle("Manage Branches");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setPrefWidth(500);
        root.setPrefHeight(450);

        // Search field
        Label searchLabel = new Label("Search branches:");
        searchField = new TextField();
        searchField.setPromptText("Filter branches...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterBranches(newVal));

        // Create new branch button
        Button createButton = new Button("Create New Branch");
        createButton.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 11px;");
        createButton.setOnAction(e -> createNewBranch());

        // Branch list
        Label branchesLabel = new Label("Branches:");
        branchesLabel.setStyle("-fx-font-weight: bold;");
        
        branchListView = new ListView<>(filteredBranches);
        branchListView.setCellFactory(lv -> new BranchCell());
        VBox.setVgrow(branchListView, Priority.ALWAYS);

        // Bottom buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshBranches());
        
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());
        closeButton.setCancelButton(true);
        
        buttonBox.getChildren().addAll(refreshButton, closeButton);

        root.getChildren().addAll(
            searchLabel,
            searchField,
            createButton,
            branchesLabel,
            branchListView,
            buttonBox
        );

        stage.setScene(new Scene(root));
        
        // Load branches initially
        refreshBranches();
    }

    /**
     * Shows the dialog.
     */
    public void show() {
        stage.show();
    }

    /**
     * Refreshes the branch list from git.
     */
    private void refreshBranches() {
        List<String> branches = gitManager.getBranches();
        allBranches.clear();
        allBranches.addAll(branches);
        filterBranches(searchField.getText());
    }

    /**
     * Filters the branch list based on search text.
     *
     * @param searchText the text to filter by
     */
    private void filterBranches(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredBranches.setAll(allBranches);
        } else {
            String lowerSearch = searchText.toLowerCase();
            List<String> filtered = allBranches.stream()
                    .filter(branch -> branch.toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
            filteredBranches.setAll(filtered);
        }
    }

    /**
     * Creates a new branch.
     */
    private void createNewBranch() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New Branch");
        dialog.setHeaderText("Enter the name for the new branch:");
        dialog.setContentText("Branch name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(branchName -> {
            if (branchName.trim().isEmpty()) {
                showError("Branch name cannot be empty");
                return;
            }

            if (gitManager.createAndCheckoutBranch(branchName.trim())) {
                showInfo("Branch '" + branchName + "' created and checked out successfully");
                refreshBranches();
                notifyBranchChange();
            } else {
                showError("Failed to create branch '" + branchName + "'");
            }
        });
    }

    /**
     * Checks out a branch.
     *
     * @param branchName the branch to checkout
     */
    private void checkoutBranch(String branchName) {
        if (gitManager.checkoutBranch(branchName)) {
            showInfo("Checked out branch '" + branchName + "'");
            refreshBranches();
            notifyBranchChange();
        } else {
            showError("Failed to checkout branch '" + branchName + "'");
        }
    }

    /**
     * Creates a new branch from an existing branch.
     *
     * @param baseBranch the branch to create from
     */
    private void createBranchFrom(String baseBranch) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Branch From");
        dialog.setHeaderText("Create a new branch from '" + baseBranch + "':");
        dialog.setContentText("Branch name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(branchName -> {
            if (branchName.trim().isEmpty()) {
                showError("Branch name cannot be empty");
                return;
            }

            if (gitManager.createBranchFrom(branchName.trim(), baseBranch)) {
                showInfo("Branch '" + branchName + "' created from '" + baseBranch + "' and checked out");
                refreshBranches();
                notifyBranchChange();
            } else {
                showError("Failed to create branch '" + branchName + "' from '" + baseBranch + "'");
            }
        });
    }

    /**
     * Deletes a branch with confirmation.
     *
     * @param branchName the branch to delete
     */
    private void deleteBranch(String branchName) {
        Alert confirmDialog = new Alert(AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete Branch");
        confirmDialog.setHeaderText("Are you sure you want to delete branch '" + branchName + "'?");
        confirmDialog.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (gitManager.deleteBranch(branchName)) {
                showInfo("Branch '" + branchName + "' deleted successfully");
                refreshBranches();
                notifyBranchChange();
            } else {
                showError("Failed to delete branch '" + branchName + "'.\nMake sure it's not the current branch.");
            }
        }
    }

    /**
     * Notifies the callback that branches have changed.
     */
    private void notifyBranchChange() {
        if (onBranchChange != null) {
            onBranchChange.accept(null);
        }
    }

    /**
     * Shows an information alert.
     *
     * @param message the message to display
     */
    private void showInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error alert.
     *
     * @param message the error message to display
     */
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Custom list cell for displaying branches with action buttons.
     */
    private class BranchCell extends ListCell<String> {
        @Override
        protected void updateItem(String branchName, boolean empty) {
            super.updateItem(branchName, empty);

            if (empty || branchName == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            HBox box = new HBox(8);
            box.setPadding(new Insets(4, 8, 4, 8));
            box.setAlignment(Pos.CENTER_LEFT);
            box.maxWidthProperty().bind(widthProperty());

            // Branch name label
            Label nameLabel = new Label(branchName);
            nameLabel.setTooltip(new javafx.scene.control.Tooltip(branchName));
            nameLabel.setMinWidth(0);
            nameLabel.setMaxWidth(200);
            Optional<String> currentBranch = gitManager.getCurrentBranch();
            boolean isCurrent = currentBranch.isPresent() && currentBranch.get().equals(branchName);

            if (isCurrent) {
                nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #007acc;");
            } else {
                nameLabel.setStyle("-fx-text-fill: #333;");
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Action buttons
            Button checkoutButton = new Button("Checkout");
            checkoutButton.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 10px;");
            checkoutButton.setDisable(isCurrent);
            checkoutButton.setOnAction(e -> checkoutBranch(branchName));

            Button createFromButton = new Button("Create from");
            createFromButton.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 10px;");
            createFromButton.setOnAction(e -> createBranchFrom(branchName));

            Button deleteButton = new Button("Delete");
            deleteButton.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 10px; -fx-text-fill: #d32f2f;");
            deleteButton.setDisable(isCurrent);
            deleteButton.setOnAction(e -> deleteBranch(branchName));

            box.getChildren().addAll(nameLabel, spacer, checkoutButton, createFromButton, deleteButton);
            setGraphic(box);
        }
    }
}
