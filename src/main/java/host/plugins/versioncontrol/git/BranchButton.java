package host.plugins.versioncontrol.git;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import managers.GitManager;

/**
 * A button component that displays the current git branch information.
 * Shows a git branch icon and the current branch name with status indicators.
 */
public class BranchButton extends Button {
    
    private final GitManager gitManager;
    private final Label statusBranchLabel;
    private final BranchStatusFormatter formatter;
    
    /**
     * Creates a new BranchButton with the given GitManager.
     * If not in a git repository, the button will be hidden.
     * 
     * @param gitManager the GitManager to use for retrieving branch information
     */
    public BranchButton(GitManager gitManager) {
        super();
        this.gitManager = gitManager;
        this.statusBranchLabel = new Label();
        this.formatter = new BranchStatusFormatter();
        
        // If not in a git repository, hide the button
        if (!gitManager.isGitRepository()) {
            System.out.println("[Git] Not in a git repository");
            setVisible(false);
            setManaged(false);
            return;
        }
        
        // Set up the button styling
        setPrefHeight(24);
        setMinHeight(24);
        setStyle("-fx-padding: 2 8 2 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-font-size: 11px;");
        
        // Create button content with icon and branch label
        HBox buttonContent = new HBox(4);
        buttonContent.setAlignment(Pos.CENTER);
        
        // Load and add the arrow-split-090 icon
        try {
            Image icon = new Image(BranchButton.class.getResourceAsStream("/host/icons/arrow-split-090.png"));
            ImageView iconView = new ImageView(icon);
            iconView.setFitWidth(14);
            iconView.setFitHeight(14);
            buttonContent.getChildren().add(iconView);
        } catch (Exception e) {
            // Fallback to text if icon can't be loaded
            Label gitLabel = new Label("Git");
            buttonContent.getChildren().add(gitLabel);
        }
        
        // Set the git status text with formatting
        updateBranchDisplay();
        buttonContent.getChildren().add(statusBranchLabel);
        
        setGraphic(buttonContent);
        
        // Button action - opens the branches dialog
        setOnAction(e -> {
            BranchesDialog dialog = new BranchesDialog(getScene().getWindow(), gitManager, v -> refresh());
            dialog.show();
        });
        
        // Subscribe to file system changes to auto-refresh
        subscribeToGitChanges();
    }
    
    /**
     * Subscribes to git file system changes to automatically refresh the button.
     */
    private void subscribeToGitChanges() {
        managers.GitStatusWatcher watcher = gitManager.getWatcher();
        if (watcher != null) {
            watcher.addListener(changeType -> {
                // Refresh on any git change
                refresh();
            });
        }
    }
    
    /**
     * Get the branch label for testing purposes.
     * 
     * @return the branch label
     */
    public Label getBranchLabel() {
        return statusBranchLabel;
    }
    
    /**
     * Refresh the branch information displayed on the button.
     */
    public void refresh() {
        if (gitManager.isGitRepository()) {
            updateBranchDisplay();
        }
    }
    
    /**
     * Updates the branch display with current status information.
     */
    private void updateBranchDisplay() {
        String branchName = gitManager.getCurrentBranch().orElse("");
        boolean hasStagedChanges = gitManager.hasStagedChanges();
        boolean hasUnstagedChanges = gitManager.hasUnstagedChanges();
        
        // Format display string (remote info will be added in future iterations)
        String displayText = formatter.format(branchName, hasStagedChanges, hasUnstagedChanges, 
                                             false, 0, 0);
        statusBranchLabel.setText(displayText);
        
        // Format tooltip
        String tooltipText = formatter.formatTooltip(branchName, hasStagedChanges, hasUnstagedChanges,
                                                     false, 0, 0);
        setTooltip(new Tooltip(tooltipText));
    }
}
