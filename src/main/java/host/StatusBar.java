package host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import filetype.FileTypeRegistry;
import host.plugins.versioncontrol.git.BranchButton;
import managers.GitManager;

public class StatusBar extends HBox {

    private final Label fileTypeLabel = new Label();
    private final Label fileSizeLabel = new Label();
    private final BranchButton branchButton;
    private final FileTypeRegistry fileTypes;

    public StatusBar(GitManager gitManager, FileTypeRegistry fileTypes) {
        super(8);
        this.fileTypes = fileTypes;
        this.branchButton = new BranchButton(gitManager);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Separator separator = new Separator(Orientation.VERTICAL);

        getChildren().addAll(branchButton, spacer, fileTypeLabel, separator, fileSizeLabel);
        setPadding(new Insets(4, 10, 4, 10));
        setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
    }

    public void update(Path path) {
        String type = fileTypes.forPath(path).name();
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            size = -1;
        }
        fileTypeLabel.setText(type);
        fileSizeLabel.setText(size >= 0 ? humanReadableSize(size) : "");
        branchButton.refresh();
    }

    public void clear() {
        fileTypeLabel.setText("");
        fileSizeLabel.setText("");
        branchButton.refresh();
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
