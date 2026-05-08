package host;

import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import search.SearchMatch;
import search.SearchService;

class GlobalSearchDialog {

    private static final Duration SEARCH_DEBOUNCE = Duration.millis(180);

    private final Stage stage;
    private final TextField queryField;
    private final ListView<SearchMatch> resultList;
    private final TextFlow statusFlow;
    private final GlobalSearchController searchController;
    private final Consumer<SearchMatch> onOpenMatch;
    private final PauseTransition searchDebounce;
    private Thread searchThread;

    GlobalSearchDialog(Window owner, Path rootPath, Consumer<SearchMatch> onOpenMatch) {
        this.onOpenMatch = onOpenMatch;
        this.searchController = new GlobalSearchController(new SearchService(rootPath));

        queryField = new TextField();
        queryField.setPromptText("Search in files...");

        resultList = new ListView<>();
        resultList.setPrefHeight(420);
        resultList.setCellFactory(lv -> new SearchMatchCell(rootPath));

        statusFlow = new TextFlow();

        searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
        searchDebounce.setOnFinished(e -> performSearch(queryField.getText()));

        VBox root = new VBox(8, queryField, resultList, statusFlow);
        root.setPadding(new Insets(12));
        root.setPrefWidth(720);

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Search in Files");
        stage.setScene(new Scene(root));

        queryField.textProperty().addListener((obs, oldV, newV) -> scheduleSearch(newV));
        queryField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            } else if (e.getCode() == KeyCode.DOWN) {
                if (!resultList.getItems().isEmpty()) {
                    resultList.getSelectionModel().selectFirst();
                    resultList.requestFocus();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (resultList.getSelectionModel().getSelectedItem() == null
                        && !resultList.getItems().isEmpty()) {
                    resultList.getSelectionModel().selectFirst();
                }
                openSelected();
            }
        });

        resultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelected();
        });

        stage.setOnHidden(e -> {
            searchDebounce.stop();
            interruptSearchThread();
        });
    }

    void show() {
        stage.show();
        Platform.runLater(queryField::requestFocus);
    }

    private void scheduleSearch(String query) {
        searchDebounce.stop();
        interruptSearchThread();
        if (!searchController.prepareQuery(query)) {
            resultList.getItems().clear();
            statusFlow.getChildren().clear();
            return;
        }
        setSearchingStatus(query);
        searchDebounce.playFromStart();
    }

    private void performSearch(String query) {
        if (query == null || query.isEmpty()) return;
        GlobalSearchController.SearchRun run = searchController.beginSearch(query);
        Thread t = new Thread(() -> runSearch(run), "global-search");
        t.setDaemon(true);
        searchThread = t;
        t.start();
    }

    private void runSearch(GlobalSearchController.SearchRun run) {
        GlobalSearchController.SearchResult result = searchController.executeSearch(run);
        if (result.ignored()) return;

        Platform.runLater(() -> {
            if (!searchController.shouldApply(run, queryField.getText())) return;
            if (result.hasError()) {
                setStatusText(result.errorText());
                return;
            }
            resultList.getItems().setAll(result.results());
            setStatusText(result.statusText());
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
        statusFlow.getChildren().setAll(prefix, term);
    }

    private void setStatusText(String status) {
        statusFlow.getChildren().setAll(new Text(status));
    }

    private void openSelected() {
        SearchMatch m = resultList.getSelectionModel().getSelectedItem();
        if (m != null) {
            onOpenMatch.accept(m);
            stage.close();
        }
    }

    private static class SearchMatchCell extends ListCell<SearchMatch> {
        private static final int MATCH_CONTEXT_CHARS = 40;

        private final Path rootPath;

        SearchMatchCell(Path rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        protected void updateItem(SearchMatch m, boolean empty) {
            super.updateItem(m, empty);
            if (empty || m == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Path rel;
            try {
                rel = rootPath.relativize(m.filePath());
            } catch (IllegalArgumentException e) {
                rel = m.filePath();
            }

            Text location = new Text(rel + ":" + m.lineNumber() + ":" + (m.columnStart() + 1) + "  ");
            location.setStyle("-fx-fill: #888888;");

            String line = m.lineText();
            int snippetStart = Math.max(0, m.columnStart() - MATCH_CONTEXT_CHARS);
            int snippetEnd = Math.min(line.length(), m.columnEnd() + MATCH_CONTEXT_CHARS);

            String preText = line.substring(snippetStart, m.columnStart());
            String matchText = line.substring(m.columnStart(), m.columnEnd());
            String postText = line.substring(m.columnEnd(), snippetEnd);

            if (snippetStart > 0) {
                preText = "..." + preText;
            }
            if (snippetEnd < line.length()) {
                postText = postText + "...";
            }

            Text pre   = new Text(preText);
            Text match = new Text(matchText);
            match.setStyle("-fx-font-weight: bold; -fx-fill: #e07b00;");
            Text post  = new Text(postText);

            setText(null);
            setGraphic(new TextFlow(location, pre, match, post));
        }
    }
}
