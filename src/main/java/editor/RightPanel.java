package editor;

import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

class RightPanel extends HBox {

    private static final double EXPANDED_DIVIDER = 0.75;
    private static final double COLLAPSED_DIVIDER = 1.0 - 0.025;

    private final Consumer<Double> onDividerChange;
    private final StackPane contentArea;
    private final Button tasksButton;

    private boolean expanded = false;
    private String selectedTab = "tasks";

    RightPanel(Consumer<Double> onDividerChange) {
        this.onDividerChange = onDividerChange;

        VBox sidebar = new VBox();
        sidebar.setPrefWidth(24);
        sidebar.setMinWidth(24);
        sidebar.setMaxWidth(24);
        sidebar.setSpacing(4);
        sidebar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 0 1;");

        tasksButton = createTabButton("Tasks", "/editor/icons/document-list.png");
        tasksButton.setOnAction(e -> selectTab("tasks"));
        tasksButton.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        sidebar.getChildren().add(tasksButton);

        contentArea = new StackPane();
        Label tasksPlaceholder = new Label("Tasks - Coming Soon");
        tasksPlaceholder.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
        contentArea.getChildren().add(tasksPlaceholder);

        getChildren().addAll(contentArea, sidebar);
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        setSpacing(0);
    }

    private void selectTab(String tabId) {
        if (selectedTab.equals(tabId)) {
            expanded = !expanded;
        } else {
            expanded = true;
        }
        selectedTab = tabId;

        onDividerChange.accept(expanded ? EXPANDED_DIVIDER : COLLAPSED_DIVIDER);

        tasksButton.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        contentArea.getChildren().clear();
        if ("tasks".equals(tabId)) {
            Label placeholder = new Label("Tasks - Coming Soon");
            placeholder.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
            contentArea.getChildren().add(placeholder);
        }
    }

    private Button createTabButton(String tooltipText, String iconPath) {
        Button btn = new Button();
        btn.setPrefSize(24, 24);
        btn.setMinSize(24, 24);
        btn.setMaxSize(24, 24);
        btn.setStyle("-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        try {
            Image icon = new Image(RightPanel.class.getResourceAsStream(iconPath));
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
