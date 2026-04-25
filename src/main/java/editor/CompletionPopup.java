package editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.eclipse.lsp4j.CompletionItem;
import org.fxmisc.richtext.CodeArea;

    /**
     * A popup window that displays code completion suggestions.
     */
    public class CompletionPopup {

    private final Popup popup;
    private final ListView<CompletionItem> listView;
    private final CodeArea codeArea;
    private Consumer<CompletionItem> onSelect;
    private String filterText = "";

    public CompletionPopup(CodeArea codeArea) {
        this.codeArea = codeArea;
        this.listView = new ListView<>();
        this.popup = new Popup();

        listView.setPrefWidth(300);
        listView.setPrefHeight(200);
        listView.setCellFactory(lv -> new CompletionCell());
        
        // Handle mouse clicks on items
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                CompletionItem selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    System.err.println("[CompletionPopup] Mouse click on: " + selected.getLabel());
                    selectCurrent();
                }
            }
        });

        // Handle key presses for keyboard navigation
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                System.err.println("[CompletionPopup] Enter pressed on: " + 
                    (listView.getSelectionModel().getSelectedItem() != null ?
                     listView.getSelectionModel().getSelectedItem().getLabel() : "null"));
                selectCurrent();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                System.err.println("[CompletionPopup] Escape pressed");
                hide();
                event.consume();
            } else if (event.getCode() == KeyCode.TAB) {
                System.err.println("[CompletionPopup] Tab pressed on: " + 
                    (listView.getSelectionModel().getSelectedItem() != null ?
                     listView.getSelectionModel().getSelectedItem().getLabel() : "null"));
                selectCurrent();
                event.consume();
            }
        });

        VBox content = new VBox(listView);
        content.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");
        popup.getContent().add(content);
        popup.setAutoHide(true);
    }

    /**
     * Show the completion popup with the given items.
     */
    public void show(List<CompletionItem> items) {
        if (items == null || items.isEmpty()) {
            hide();
            return;
        }

        Platform.runLater(() -> {
            listView.getItems().setAll(items);
            listView.getSelectionModel().selectFirst();
            
            // Position the popup below the cursor
            // getCaretBounds() returns bounds in screen coordinates
            codeArea.getCaretBounds().ifPresent(bounds -> {
                popup.show(codeArea.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 2);
            });
            listView.requestFocus();
        });
    }

    /**
     * Filter completion items based on typed text.
     */
    public void filter(String text, List<CompletionItem> allItems) {
        this.filterText = text.toLowerCase();
        
        List<CompletionItem> filtered = new ArrayList<>();
        for (CompletionItem item : allItems) {
            String label = item.getLabel() != null ? item.getLabel().toLowerCase() : "";
            if (label.contains(filterText)) {
                filtered.add(item);
            }
        }
        
        if (filtered.isEmpty()) {
            hide();
        } else {
            listView.getItems().setAll(filtered);
            listView.getSelectionModel().selectFirst();
        }
    }

    /**
     * Set the callback for when an item is selected.
     */
    public void setOnSelect(Consumer<CompletionItem> callback) {
        this.onSelect = callback;
    }

    /**
     * Select and insert the currently selected completion item.
     */
    public void selectCurrent() {
        CompletionItem selected = listView.getSelectionModel().getSelectedItem();
        System.err.println("[CompletionPopup] selectCurrent called. Selected: " + (selected != null ? selected.getLabel() : "null") + ", onSelect: " + (onSelect != null));
        if (selected != null && onSelect != null) {
            System.err.println("[CompletionPopup] Calling onSelect.accept()");
            onSelect.accept(selected);
        }
        hide();
    }

    /**
     * Select the previous item in the list.
     */
    public void selectPrevious() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            listView.getSelectionModel().select(idx - 1);
        }
    }

    /**
     * Select the next item in the list.
     */
    public void selectNext() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx + 1 >= 0) {
            listView.getSelectionModel().select(Math.min(idx + 1, listView.getItems().size() - 1));
        } else if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }
    }

    /**
     * Hide the completion popup.
     */
    public void hide() {
        popup.hide();
        filterText = "";
    }

    /**
     * Check if the popup is currently showing.
     */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /**
     * Custom cell renderer for completion items.
     */
    private static class CompletionCell extends ListCell<CompletionItem> {
        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                String label = item.getLabel() != null ? item.getLabel() : "?";
                String detail = item.getDetail() != null ? " " + item.getDetail() : "";
                setText(label + detail);
                // Optional: Add icon based on completion kind
                setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11;");
            }
        }
    }
}
