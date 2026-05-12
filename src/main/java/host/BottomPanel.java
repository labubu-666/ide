package host;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class BottomPanel extends VBox {

    private final Button outputTabButton;
    private final TextArea outputPanel;
    private boolean outputPanelVisible;

    BottomPanel() {
        outputTabButton = new Button("Output");
        outputTabButton.setOnAction(e -> toggleOutputPanel());

        HBox tabBar = new HBox(outputTabButton);
        tabBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0; -fx-padding: 4 8 4 8;");

        outputPanel = new TextArea();
        outputPanel.setEditable(false);
        outputPanel.setWrapText(true);
        outputPanel.setPrefRowCount(8);
        outputPanel.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        outputPanel.setVisible(false);
        outputPanel.setManaged(false);

        updateOutputTabStyle();
        getChildren().addAll(tabBar, outputPanel);
    }

    TextArea outputArea() {
        return outputPanel;
    }

    void showOutputPanel() {
        if (outputPanelVisible) {
            return;
        }
        outputPanelVisible = true;
        outputPanel.setVisible(true);
        outputPanel.setManaged(true);
        updateOutputTabStyle();
    }

    void appendOutput(String line) {
        showOutputPanel();
        if (line == null || line.isEmpty()) {
            return;
        }
        if (!outputPanel.getText().isEmpty()) {
            outputPanel.appendText("\n");
        }
        outputPanel.appendText(line);
    }

    private void toggleOutputPanel() {
        outputPanelVisible = !outputPanelVisible;
        outputPanel.setVisible(outputPanelVisible);
        outputPanel.setManaged(outputPanelVisible);
        updateOutputTabStyle();
    }

    private void updateOutputTabStyle() {
        if (outputPanelVisible) {
            outputTabButton.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 1;");
        } else {
            outputTabButton.setStyle("");
        }
    }
}
