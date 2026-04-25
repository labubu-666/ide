package settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class SettingsDialog {

    private final Stage stage;
    private final TextField interpreterField;
    private final TextField pylspField;
    private final TextArea interpreterOutputArea;
    private final TextArea pylspOutputArea;
    private final TextField tsServerField;
    private final TextArea tsServerOutputArea;
    private final TextField sassServerField;
    private final TextArea sassServerOutputArea;
    private final TextField yamlServerField;
    private final TextArea yamlServerOutputArea;
    private final TextField jsonServerField;
    private final TextArea jsonServerOutputArea;
    private final TextField markdownServerField;
    private final TextArea markdownServerOutputArea;

    public SettingsDialog(Window owner) {
        stage = new Stage();
        stage.setTitle("Settings");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);

        // --- Python tab content ---
        Label interpLabel = new Label("Interpreter path:");

         interpreterField = new TextField(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER));
         HBox.setHgrow(interpreterField, Priority.ALWAYS);

         Button browseInterpBtn = new Button("Browse...");
         browseInterpBtn.setOnAction(e -> browse("Select Python Interpreter", interpreterField));

         HBox interpreterRow = new HBox(8, interpreterField, browseInterpBtn);

         interpreterOutputArea = new TextArea();
         interpreterOutputArea.setEditable(false);
         interpreterOutputArea.setWrapText(true);
         interpreterOutputArea.setPrefRowCount(2);
         interpreterOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

         Button testInterpBtn = new Button("Test");
         testInterpBtn.setOnAction(e -> testExecutable(interpreterField, interpreterOutputArea));

         Label pylspLabel = new Label("pylsp path:");

         pylspField = new TextField(Settings.get(Settings.SettingKey.PYLSP_PATH));
        HBox.setHgrow(pylspField, Priority.ALWAYS);

        Button browsePylspBtn = new Button("Browse...");
        browsePylspBtn.setOnAction(e -> browse("Select pylsp Executable", pylspField));

        HBox pylspRow = new HBox(8, pylspField, browsePylspBtn);

        pylspOutputArea = new TextArea();
        pylspOutputArea.setEditable(false);
        pylspOutputArea.setWrapText(true);
        pylspOutputArea.setPrefRowCount(2);
        pylspOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Button testPylspBtn = new Button("Test");
        testPylspBtn.setOnAction(e -> testExecutable(pylspField, pylspOutputArea));

        VBox pythonContent = new VBox(6,
                interpLabel,
                interpreterRow,
                testInterpBtn,
                interpreterOutputArea,
                pylspLabel,
                pylspRow,
                testPylspBtn,
                pylspOutputArea);
        pythonContent.setPadding(new Insets(12));

         // --- TypeScript tab content ---
         Label tsServerLabel = new Label("Language server command:");

         tsServerField = new TextField(Settings.get(Settings.SettingKey.TS_SERVER_PATH));
         HBox.setHgrow(tsServerField, Priority.ALWAYS);

         Button browseTsBtn = new Button("Browse...");
         browseTsBtn.setOnAction(e -> browse("Select typescript-language-server", tsServerField));

         HBox tsServerRow = new HBox(8, tsServerField, browseTsBtn);

         tsServerOutputArea = new TextArea();
         tsServerOutputArea.setEditable(false);
         tsServerOutputArea.setWrapText(true);
         tsServerOutputArea.setPrefRowCount(2);
         tsServerOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

         Button testTsBtn = new Button("Test");
         testTsBtn.setOnAction(e -> testCommand(tsServerField, tsServerOutputArea));

         VBox tsContent = new VBox(6,
                 tsServerLabel,
                 tsServerRow,
                 testTsBtn,
                 tsServerOutputArea);
         tsContent.setPadding(new Insets(12));

         // --- SASS/SCSS tab content ---
         Label sassServerLabel = new Label("Language server command:");

         sassServerField = new TextField(Settings.get(Settings.SettingKey.SASS_SERVER_PATH));
        HBox.setHgrow(sassServerField, Priority.ALWAYS);

        Button browseSassBtn = new Button("Browse...");
        browseSassBtn.setOnAction(e -> browse("Select some-sass-language-server", sassServerField));

        HBox sassServerRow = new HBox(8, sassServerField, browseSassBtn);

        sassServerOutputArea = new TextArea();
        sassServerOutputArea.setEditable(false);
        sassServerOutputArea.setWrapText(true);
        sassServerOutputArea.setPrefRowCount(2);
        sassServerOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Button testSassBtn = new Button("Test");
        testSassBtn.setOnAction(e -> testCommand(sassServerField, sassServerOutputArea));

        VBox sassContent = new VBox(6,
                sassServerLabel,
                sassServerRow,
                testSassBtn,
                sassServerOutputArea);
        sassContent.setPadding(new Insets(12));

        // --- YAML tab content ---
        Label yamlServerLabel = new Label("Language server command:");

        yamlServerField = new TextField(Settings.get(Settings.SettingKey.YAML_SERVER_PATH));
        HBox.setHgrow(yamlServerField, Priority.ALWAYS);

        Button browseYamlBtn = new Button("Browse...");
        browseYamlBtn.setOnAction(e -> browse("Select yaml-language-server", yamlServerField));

        HBox yamlServerRow = new HBox(8, yamlServerField, browseYamlBtn);

        yamlServerOutputArea = new TextArea();
        yamlServerOutputArea.setEditable(false);
        yamlServerOutputArea.setWrapText(true);
        yamlServerOutputArea.setPrefRowCount(2);
        yamlServerOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Button testYamlBtn = new Button("Test");
        testYamlBtn.setOnAction(e -> testCommand(yamlServerField, yamlServerOutputArea));

         VBox yamlContent = new VBox(6,
                 yamlServerLabel,
                 yamlServerRow,
                 testYamlBtn,
                 yamlServerOutputArea);
         yamlContent.setPadding(new Insets(12));

         // --- JSON tab content ---
         Label jsonServerLabel = new Label("Language server command:");

         jsonServerField = new TextField(Settings.get(Settings.SettingKey.JSON_SERVER_PATH));
         HBox.setHgrow(jsonServerField, Priority.ALWAYS);

         Button browseJsonBtn = new Button("Browse...");
         browseJsonBtn.setOnAction(e -> browse("Select vscode-json-languageserver", jsonServerField));

         HBox jsonServerRow = new HBox(8, jsonServerField, browseJsonBtn);

         jsonServerOutputArea = new TextArea();
         jsonServerOutputArea.setEditable(false);
         jsonServerOutputArea.setWrapText(true);
         jsonServerOutputArea.setPrefRowCount(2);
         jsonServerOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

         Button testJsonBtn = new Button("Test");
         testJsonBtn.setOnAction(e -> testCommand(jsonServerField, jsonServerOutputArea));

         VBox jsonContent = new VBox(6,
                 jsonServerLabel,
                 jsonServerRow,
                 testJsonBtn,
                 jsonServerOutputArea);
         jsonContent.setPadding(new Insets(12));

         // --- Markdown tab content ---
         Label markdownServerLabel = new Label("Language server command:");

         markdownServerField = new TextField(Settings.get(Settings.SettingKey.MARKDOWN_SERVER_PATH));
         HBox.setHgrow(markdownServerField, Priority.ALWAYS);

         Button browseMarkdownBtn = new Button("Browse...");
         browseMarkdownBtn.setOnAction(e -> browse("Select markdown language server", markdownServerField));

         HBox markdownServerRow = new HBox(8, markdownServerField, browseMarkdownBtn);

         markdownServerOutputArea = new TextArea();
         markdownServerOutputArea.setEditable(false);
         markdownServerOutputArea.setWrapText(true);
         markdownServerOutputArea.setPrefRowCount(2);
         markdownServerOutputArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

         Button testMarkdownBtn = new Button("Test");
         testMarkdownBtn.setOnAction(e -> testCommand(markdownServerField, markdownServerOutputArea));

         VBox markdownContent = new VBox(6,
                 markdownServerLabel,
                 markdownServerRow,
                 testMarkdownBtn,
                 markdownServerOutputArea);
         markdownContent.setPadding(new Insets(12));

         // --- SDK tab: contains Python, TypeScript, SASS, YAML, JSON, and Markdown nested tabs ---
         Tab pythonTab = new Tab("Python", pythonContent);
         pythonTab.setClosable(false);

         Tab typescriptTab = new Tab("TypeScript", tsContent);
         typescriptTab.setClosable(false);

         Tab sassTab = new Tab("SASS/SCSS", sassContent);
         sassTab.setClosable(false);

         Tab yamlTab = new Tab("YAML", yamlContent);
         yamlTab.setClosable(false);

         Tab jsonTab = new Tab("JSON", jsonContent);
         jsonTab.setClosable(false);

         Tab markdownTab = new Tab("Markdown", markdownContent);
         markdownTab.setClosable(false);

         TabPane sdkContent = new TabPane(pythonTab, typescriptTab, sassTab, yamlTab, jsonTab, markdownTab);
        sdkContent.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Project tab: contains SDK nested tab ---
        Tab sdkTab = new Tab("SDK", sdkContent);
        sdkTab.setClosable(false);

        TabPane projectContent = new TabPane(sdkTab);
        projectContent.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Top-level tab: Project ---
        Tab projectTab = new Tab("Project", projectContent);
        projectTab.setClosable(false);

        TabPane rootTabs = new TabPane(projectTab);
        rootTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(rootTabs, Priority.ALWAYS);

        // --- Dialog buttons ---
          Button saveBtn = new Button("Save");
          saveBtn.setDefaultButton(true);
          saveBtn.setOnAction(e -> {
              Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, interpreterField.getText());
              Settings.set(Settings.SettingKey.PYLSP_PATH, pylspField.getText());
              Settings.set(Settings.SettingKey.TS_SERVER_PATH, tsServerField.getText());
              Settings.set(Settings.SettingKey.SASS_SERVER_PATH, sassServerField.getText());
              Settings.set(Settings.SettingKey.YAML_SERVER_PATH, yamlServerField.getText());
              Settings.set(Settings.SettingKey.JSON_SERVER_PATH, jsonServerField.getText());
              Settings.set(Settings.SettingKey.MARKDOWN_SERVER_PATH, markdownServerField.getText());
              stage.close();
          });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(8, saveBtn, cancelBtn);
        buttonRow.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(12, rootTabs, buttonRow);
        root.setPadding(new Insets(16));
        root.setPrefWidth(500);
        root.setPrefHeight(420);

        stage.setScene(new Scene(root));
    }

    public void show() {
        stage.show();
    }

    private void browse(String title, TextField target) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        String current = target.getText().strip();
        File initial = new File(current);
        if (initial.getParentFile() != null && initial.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(initial.getParentFile());
        }
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            target.setText(file.getAbsolutePath());
        }
    }

    // For full command strings (e.g. "npx --yes typescript-language-server --stdio"):
    // replaces --stdio with --version to probe the server.
    private void testCommand(TextField field, TextArea output) {
        String raw = field.getText().strip();
        if (raw.isEmpty()) {
            output.setText("No command specified.");
            output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: red;");
            return;
        }
        String[] parts = raw.split("\\s+");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(parts));
        cmd.remove("--stdio");
        cmd.add("--version");

        output.setText("Testing...");
        output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String result;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    result = reader.lines().collect(Collectors.joining("\n")).strip();
                }
                int exitCode = process.waitFor();
                if (result.isEmpty()) result = "(no output)";
                if (exitCode != 0) result += "\n[exit code: " + exitCode + "]";
                String finalResult = result;
                boolean success = exitCode == 0;
                Platform.runLater(() -> {
                    output.setText(finalResult);
                    output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: "
                            + (success ? "green" : "red") + ";");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    output.setText("Error: " + ex.getMessage());
                    output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: red;");
                });
            }
        }, "command-test");
        thread.setDaemon(true);
        thread.start();
    }

    private void testExecutable(TextField field, TextArea output) {
        String path = field.getText().strip();
        if (path.isEmpty()) {
            output.setText("No path specified.");
            output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: red;");
            return;
        }

        output.setText("Testing...");
        output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String result;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    result = reader.lines().collect(Collectors.joining("\n")).strip();
                }
                int exitCode = process.waitFor();
                if (result.isEmpty()) result = "(no output)";
                if (exitCode != 0) result += "\n[exit code: " + exitCode + "]";
                String finalResult = result;
                boolean success = exitCode == 0;
                Platform.runLater(() -> {
                    output.setText(finalResult);
                    output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: "
                            + (success ? "green" : "red") + ";");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    output.setText("Error: " + ex.getMessage());
                    output.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: red;");
                });
            }
        }, "executable-test");
        thread.setDaemon(true);
        thread.start();
    }
}
