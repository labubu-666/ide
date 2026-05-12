package settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
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
    private final Path projectRoot;

    private static final class ScopeEditor {
        private final VBox content;
        private final Runnable saveAction;

        private ScopeEditor(VBox content, Runnable saveAction) {
            this.content = content;
            this.saveAction = saveAction;
        }
    }

    public SettingsDialog(Window owner, Path projectRoot) {
        stage = new Stage();
        this.projectRoot = projectRoot;
        stage.setTitle("Settings");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);

        ScopeEditor globalEditor = buildScopeEditor(Settings.Scope.GLOBAL);
        ScopeEditor projectEditor = buildScopeEditor(Settings.Scope.PROJECT);

        Tab globalTab = new Tab("Global", globalEditor.content);
        globalTab.setClosable(false);

        Tab projectTab = new Tab("Project", projectEditor.content);
        projectTab.setClosable(false);

        TabPane rootTabs = new TabPane(globalTab, projectTab);
        rootTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(rootTabs, Priority.ALWAYS);

        // --- Dialog buttons ---
        Button saveBtn = new Button("Save");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            globalEditor.saveAction.run();
            projectEditor.saveAction.run();
            stage.close();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(8, saveBtn, cancelBtn);
        buttonRow.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(12, rootTabs, buttonRow);
        root.setPadding(new Insets(16));
        root.setPrefWidth(800);
        root.setPrefHeight(600);

        stage.setScene(new Scene(root));
    }

    private ScopeEditor buildScopeEditor(Settings.Scope scope) {
        Set<Settings.SettingKey> projectResetSdkKeys = new HashSet<>();

        // --- Python tab content ---
        Label interpLabel = new Label("Interpreter command:");

        TextField interpreterField = new TextField(Settings.get(scope, Settings.SettingKey.PYTHON_INTERPRETER));
        HBox.setHgrow(interpreterField, Priority.ALWAYS);
        Label interpreterStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.PYTHON_INTERPRETER, interpreterField, interpreterStateLabel, projectResetSdkKeys);
        Button interpreterDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.PYTHON_INTERPRETER,
                interpreterField,
                interpreterStateLabel,
                projectResetSdkKeys);
        HBox interpreterRow = new HBox(8, interpreterField, interpreterDefaultBtn);

        TextArea interpreterOutputArea = createOutputArea();

        Button testInterpBtn = new Button("Test");
        testInterpBtn.setOnAction(e -> testCommand(interpreterField, interpreterOutputArea, projectRoot));

        Label pylspLabel = new Label("Language server command:");

        TextField pylspField = new TextField(Settings.get(scope, Settings.SettingKey.PYLSP_PATH));
        HBox.setHgrow(pylspField, Priority.ALWAYS);
        Label pylspStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.PYLSP_PATH, pylspField, pylspStateLabel, projectResetSdkKeys);
        Button pylspDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.PYLSP_PATH,
                pylspField,
                pylspStateLabel,
                projectResetSdkKeys);
        HBox pylspRow = new HBox(8, pylspField, pylspDefaultBtn);

        TextArea pylspOutputArea = createOutputArea();

        Button testPylspBtn = new Button("Test");
        testPylspBtn.setOnAction(e -> testCommand(pylspField, pylspOutputArea, projectRoot));

        VBox pythonContent = new VBox(6,
                interpLabel,
                interpreterRow,
                interpreterStateLabel,
                testInterpBtn,
                interpreterOutputArea,
                pylspLabel,
                pylspRow,
                pylspStateLabel,
                testPylspBtn,
                pylspOutputArea);
        pythonContent.setPadding(new Insets(12));

        // --- TypeScript tab content ---
        Label tsServerLabel = new Label("Language server command:");

        TextField tsServerField = new TextField(Settings.get(scope, Settings.SettingKey.TS_SERVER_PATH));
        HBox.setHgrow(tsServerField, Priority.ALWAYS);
        Label tsStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.TS_SERVER_PATH, tsServerField, tsStateLabel, projectResetSdkKeys);

        Button tsDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.TS_SERVER_PATH,
                tsServerField,
                tsStateLabel,
                projectResetSdkKeys);

        Button browseTsBtn = new Button("Browse...");
        browseTsBtn.setOnAction(e -> browse("Select typescript-language-server", tsServerField));

        HBox tsServerRow = new HBox(8, tsServerField, browseTsBtn, tsDefaultBtn);

        TextArea tsServerOutputArea = createOutputArea();

        Button testTsBtn = new Button("Test");
        testTsBtn.setOnAction(e -> testCommand(tsServerField, tsServerOutputArea, projectRoot));

        VBox tsContent = new VBox(6,
                tsServerLabel,
                tsServerRow,
                tsStateLabel,
                testTsBtn,
                tsServerOutputArea);
        tsContent.setPadding(new Insets(12));

        // --- SASS/SCSS tab content ---
        Label sassServerLabel = new Label("Language server command:");

        TextField sassServerField = new TextField(Settings.get(scope, Settings.SettingKey.SASS_SERVER_PATH));
        HBox.setHgrow(sassServerField, Priority.ALWAYS);
        Label sassStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.SASS_SERVER_PATH, sassServerField, sassStateLabel, projectResetSdkKeys);

        Button sassDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.SASS_SERVER_PATH,
                sassServerField,
                sassStateLabel,
                projectResetSdkKeys);

        Button browseSassBtn = new Button("Browse...");
        browseSassBtn.setOnAction(e -> browse("Select some-sass-language-server", sassServerField));

        HBox sassServerRow = new HBox(8, sassServerField, browseSassBtn, sassDefaultBtn);

        TextArea sassServerOutputArea = createOutputArea();

        Button testSassBtn = new Button("Test");
        testSassBtn.setOnAction(e -> testCommand(sassServerField, sassServerOutputArea, projectRoot));

        VBox sassContent = new VBox(6,
                sassServerLabel,
                sassServerRow,
                sassStateLabel,
                testSassBtn,
                sassServerOutputArea);
        sassContent.setPadding(new Insets(12));

        // --- YAML tab content ---
        Label yamlServerLabel = new Label("Language server command:");

        TextField yamlServerField = new TextField(Settings.get(scope, Settings.SettingKey.YAML_SERVER_PATH));
        HBox.setHgrow(yamlServerField, Priority.ALWAYS);
        Label yamlStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.YAML_SERVER_PATH, yamlServerField, yamlStateLabel, projectResetSdkKeys);

        Button yamlDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.YAML_SERVER_PATH,
                yamlServerField,
                yamlStateLabel,
                projectResetSdkKeys);

        Button browseYamlBtn = new Button("Browse...");
        browseYamlBtn.setOnAction(e -> browse("Select yaml-language-server", yamlServerField));

        HBox yamlServerRow = new HBox(8, yamlServerField, browseYamlBtn, yamlDefaultBtn);

        TextArea yamlServerOutputArea = createOutputArea();

        Button testYamlBtn = new Button("Test");
        testYamlBtn.setOnAction(e -> testCommand(yamlServerField, yamlServerOutputArea, projectRoot));

        VBox yamlContent = new VBox(6,
                yamlServerLabel,
                yamlServerRow,
                yamlStateLabel,
                testYamlBtn,
                yamlServerOutputArea);
        yamlContent.setPadding(new Insets(12));

        // --- JSON tab content ---
        Label jsonServerLabel = new Label("Language server command:");

        TextField jsonServerField = new TextField(Settings.get(scope, Settings.SettingKey.JSON_SERVER_PATH));
        HBox.setHgrow(jsonServerField, Priority.ALWAYS);
        Label jsonStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.JSON_SERVER_PATH, jsonServerField, jsonStateLabel, projectResetSdkKeys);

        Button jsonDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.JSON_SERVER_PATH,
                jsonServerField,
                jsonStateLabel,
                projectResetSdkKeys);

        Button browseJsonBtn = new Button("Browse...");
        browseJsonBtn.setOnAction(e -> browse("Select vscode-json-languageserver", jsonServerField));

        HBox jsonServerRow = new HBox(8, jsonServerField, browseJsonBtn, jsonDefaultBtn);

        TextArea jsonServerOutputArea = createOutputArea();

        Button testJsonBtn = new Button("Test");
        testJsonBtn.setOnAction(e -> testCommand(jsonServerField, jsonServerOutputArea, projectRoot));

        VBox jsonContent = new VBox(6,
                jsonServerLabel,
                jsonServerRow,
                jsonStateLabel,
                testJsonBtn,
                jsonServerOutputArea);
        jsonContent.setPadding(new Insets(12));

        // --- Markdown tab content ---
        Label markdownServerLabel = new Label("Language server command:");

        TextField markdownServerField = new TextField(Settings.get(scope, Settings.SettingKey.MARKDOWN_SERVER_PATH));
        HBox.setHgrow(markdownServerField, Priority.ALWAYS);
        Label markdownStateLabel = new Label();
        configureSdkField(scope, Settings.SettingKey.MARKDOWN_SERVER_PATH, markdownServerField, markdownStateLabel, projectResetSdkKeys);

        Button markdownDefaultBtn = createSdkSetDefaultButton(
                scope,
                Settings.SettingKey.MARKDOWN_SERVER_PATH,
                markdownServerField,
                markdownStateLabel,
                projectResetSdkKeys);

        Button browseMarkdownBtn = new Button("Browse...");
        browseMarkdownBtn.setOnAction(e -> browse("Select markdown language server", markdownServerField));

        HBox markdownServerRow = new HBox(8, markdownServerField, browseMarkdownBtn, markdownDefaultBtn);

        TextArea markdownServerOutputArea = createOutputArea();

        Button testMarkdownBtn = new Button("Test");
        testMarkdownBtn.setOnAction(e -> testCommand(markdownServerField, markdownServerOutputArea, projectRoot));

        VBox markdownContent = new VBox(6,
                markdownServerLabel,
                markdownServerRow,
                markdownStateLabel,
                testMarkdownBtn,
                markdownServerOutputArea);
        markdownContent.setPadding(new Insets(12));

        Map<String, Settings.RunConfiguration> editableRunConfigurations =
                new LinkedHashMap<>(Settings.getRunConfigurations(scope));
        Map<String, Settings.RunConfiguration> globalRunConfigurations =
                new LinkedHashMap<>(Settings.getRunConfigurations(Settings.Scope.GLOBAL));

        Label runConfigListLabel = new Label("Configurations:");
        ListView<String> runConfigList = new ListView<>();
        runConfigList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        refreshRunConfigurationList(scope, runConfigList, editableRunConfigurations, globalRunConfigurations);
        runConfigList.setPrefHeight(120);

        Label runConfigKeyLabel = new Label("Key:");
        TextField runConfigKeyField = new TextField();
        Label runConfigStateLabel = new Label();

        Label preCommandsLabel = new Label("Pre commands (one per line):");
        TextArea preCommandsArea = new TextArea();
        preCommandsArea.setPrefRowCount(4);

        Label commandsLabel = new Label("Commands (one per line):");
        TextArea commandsArea = new TextArea();
        commandsArea.setPrefRowCount(4);

        Label postCommandsLabel = new Label("Post commands (one per line):");
        TextArea postCommandsArea = new TextArea();
        postCommandsArea.setPrefRowCount(4);

        runConfigList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                runConfigStateLabel.setText("No configuration selected");
                return;
            }
            runConfigKeyField.setText(newValue);
            Settings.RunConfiguration selectedConfig = editableRunConfigurations.get(newValue);
            if (selectedConfig == null && scope == Settings.Scope.PROJECT) {
                selectedConfig = globalRunConfigurations.get(newValue);
            }
            if (selectedConfig == null) {
                preCommandsArea.clear();
                commandsArea.clear();
                postCommandsArea.clear();
                runConfigStateLabel.setText("No configuration selected");
                return;
            }
            preCommandsArea.setText(joinCommandLines(selectedConfig.preCommands()));
            commandsArea.setText(joinCommandLines(selectedConfig.commands()));
            postCommandsArea.setText(joinCommandLines(selectedConfig.postCommands()));
            updateRunConfigurationStateLabel(scope, newValue, editableRunConfigurations, globalRunConfigurations, runConfigStateLabel);
        });

        runConfigKeyField.textProperty().addListener((obs, oldValue, newValue) -> {
            String key = newValue == null ? "" : newValue.strip();
            if (key.isEmpty()) {
                runConfigStateLabel.setText("No configuration selected");
                return;
            }
            updateRunConfigurationStateLabel(scope, key, editableRunConfigurations, globalRunConfigurations, runConfigStateLabel);
        });

        Button saveRunConfigBtn = new Button("Save configuration");
        saveRunConfigBtn.setOnAction(e -> {
            String selectedKey = runConfigList.getSelectionModel().getSelectedItem();
            String key = runConfigKeyField.getText().strip();
            if (key.isEmpty()) {
                return;
            }

            if (selectedKey != null && !selectedKey.equals(key)) {
                editableRunConfigurations.remove(selectedKey);
            }

            Settings.RunConfiguration runConfiguration = new Settings.RunConfiguration(
                    parseCommandLines(preCommandsArea.getText()),
                    parseCommandLines(commandsArea.getText()),
                    parseCommandLines(postCommandsArea.getText()));
            editableRunConfigurations.put(key, runConfiguration);

            refreshRunConfigurationList(scope, runConfigList, editableRunConfigurations, globalRunConfigurations);
            runConfigList.getSelectionModel().select(key);
            updateRunConfigurationStateLabel(scope, key, editableRunConfigurations, globalRunConfigurations, runConfigStateLabel);
        });

        Button removeRunConfigBtn = new Button("Remove configuration");
        removeRunConfigBtn.setOnAction(e -> {
            String selectedKey = runConfigList.getSelectionModel().getSelectedItem();
            if (selectedKey == null) {
                return;
            }
            editableRunConfigurations.remove(selectedKey);
            refreshRunConfigurationList(scope, runConfigList, editableRunConfigurations, globalRunConfigurations);

            if (scope == Settings.Scope.PROJECT && globalRunConfigurations.containsKey(selectedKey)) {
                runConfigList.getSelectionModel().select(selectedKey);
                return;
            }

            runConfigList.getSelectionModel().clearSelection();
            clearRunConfigurationEditor(runConfigKeyField, preCommandsArea, commandsArea, postCommandsArea, runConfigStateLabel);
        });

        Button setDefaultRunConfigBtn = new Button("Set default");
        setDefaultRunConfigBtn.setOnAction(e -> {
            String selectedKey = runConfigList.getSelectionModel().getSelectedItem();
            if (selectedKey == null || selectedKey.isBlank()) {
                return;
            }

            editableRunConfigurations.remove(selectedKey);
            refreshRunConfigurationList(scope, runConfigList, editableRunConfigurations, globalRunConfigurations);

            if (scope == Settings.Scope.PROJECT && globalRunConfigurations.containsKey(selectedKey)) {
                runConfigList.getSelectionModel().select(selectedKey);
                return;
            }

            runConfigList.getSelectionModel().clearSelection();
            clearRunConfigurationEditor(runConfigKeyField, preCommandsArea, commandsArea, postCommandsArea, runConfigStateLabel);
        });

        HBox runConfigButtons = new HBox(8, saveRunConfigBtn, removeRunConfigBtn, setDefaultRunConfigBtn);

        Label runConfigHint = new Label(scope == Settings.Scope.PROJECT
                ? "Project overrides Global. Set default removes the project override and inherits Global."
                : "Global is the base scope. Set default removes the global run configuration.");
        runConfigHint.setWrapText(true);

        VBox runConfigurationsContent = new VBox(6,
                runConfigHint,
                runConfigListLabel,
                runConfigList,
                runConfigKeyLabel,
                runConfigKeyField,
                runConfigStateLabel,
                preCommandsLabel,
                preCommandsArea,
                commandsLabel,
                commandsArea,
                postCommandsLabel,
                postCommandsArea,
                runConfigButtons);
        runConfigurationsContent.setPadding(new Insets(12));

        // --- SDK tab ---
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

        Tab sdkTab = new Tab("SDK", sdkContent);
        sdkTab.setClosable(false);

        Tab runConfigurationsTab = new Tab("Run configurations", runConfigurationsContent);
        runConfigurationsTab.setClosable(false);

        TabPane scopeContent = new TabPane(sdkTab, runConfigurationsTab);
        scopeContent.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(scopeContent, Priority.ALWAYS);

        Label scopeHint = new Label(scope == Settings.Scope.PROJECT
                ? "Project settings override Global settings. Set default in Project reverts to inherited Global values."
                : "Global settings are base values. Set default in Global reverts SDK fields to built-in defaults.");
        scopeHint.setWrapText(true);

        VBox scopeContainer = new VBox(8, scopeHint, scopeContent);
        VBox.setVgrow(scopeContent, Priority.ALWAYS);

        Runnable saveAction = () -> {
            saveSdkValue(scope, Settings.SettingKey.PYTHON_INTERPRETER, interpreterField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.PYLSP_PATH, pylspField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.TS_SERVER_PATH, tsServerField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.SASS_SERVER_PATH, sassServerField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.YAML_SERVER_PATH, yamlServerField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.JSON_SERVER_PATH, jsonServerField.getText(), projectResetSdkKeys);
            saveSdkValue(scope, Settings.SettingKey.MARKDOWN_SERVER_PATH, markdownServerField.getText(), projectResetSdkKeys);
            Settings.setRunConfigurations(scope, editableRunConfigurations);
        };

        return new ScopeEditor(scopeContainer, saveAction);
    }

    private static void saveSdkValue(
            Settings.Scope scope,
            Settings.SettingKey key,
            String value,
            Set<Settings.SettingKey> projectResetSdkKeys) {
        if (scope == Settings.Scope.PROJECT && projectResetSdkKeys.contains(key)) {
            Settings.set(scope, key, "");
            return;
        }
        Settings.set(scope, key, value);
    }

    private Button createSdkSetDefaultButton(
            Settings.Scope scope,
            Settings.SettingKey key,
            TextField field,
            Label stateLabel,
            Set<Settings.SettingKey> projectResetSdkKeys) {
        Button button = new Button("Set default");
        button.setOnAction(e -> {
            if (scope == Settings.Scope.GLOBAL) {
                field.setText(key.getDefaultValue());
            } else {
                field.setText(Settings.get(Settings.Scope.GLOBAL, key));
                projectResetSdkKeys.add(key);
            }
            updateSdkStateLabel(scope, key, field, stateLabel, projectResetSdkKeys);
        });
        return button;
    }

    private void configureSdkField(
            Settings.Scope scope,
            Settings.SettingKey key,
            TextField field,
            Label stateLabel,
            Set<Settings.SettingKey> projectResetSdkKeys) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            projectResetSdkKeys.remove(key);
            updateSdkStateLabel(scope, key, field, stateLabel, projectResetSdkKeys);
        });
        updateSdkStateLabel(scope, key, field, stateLabel, projectResetSdkKeys);
    }

    private static void updateSdkStateLabel(
            Settings.Scope scope,
            Settings.SettingKey key,
            TextField field,
            Label stateLabel,
            Set<Settings.SettingKey> projectResetSdkKeys) {
        String value = normalize(field.getText());
        String text;

        if (scope == Settings.Scope.GLOBAL) {
            text = value.equals(key.getDefaultValue()) ? "Default" : "Customized";
        } else if (projectResetSdkKeys.contains(key)) {
            text = "Inherited from Global";
        } else {
            String globalValue = normalize(Settings.get(Settings.Scope.GLOBAL, key));
            text = value.equals(globalValue) ? "Inherited from Global" : "Overrides Global";
        }

        stateLabel.setText(text);
    }

    private static void refreshRunConfigurationList(
            Settings.Scope scope,
            ListView<String> runConfigList,
            Map<String, Settings.RunConfiguration> scopeRunConfigurations,
            Map<String, Settings.RunConfiguration> globalRunConfigurations) {
        String selected = runConfigList.getSelectionModel().getSelectedItem();
        Set<String> keys = new LinkedHashSet<>(scopeRunConfigurations.keySet());
        if (scope == Settings.Scope.PROJECT) {
            keys.addAll(globalRunConfigurations.keySet());
        }

        runConfigList.getItems().setAll(keys);
        if (selected != null && runConfigList.getItems().contains(selected)) {
            runConfigList.getSelectionModel().select(selected);
        }
    }

    private static void updateRunConfigurationStateLabel(
            Settings.Scope scope,
            String key,
            Map<String, Settings.RunConfiguration> scopeRunConfigurations,
            Map<String, Settings.RunConfiguration> globalRunConfigurations,
            Label stateLabel) {
        String normalizedKey = normalize(key);
        if (normalizedKey.isEmpty()) {
            stateLabel.setText("No configuration selected");
            return;
        }

        if (scope == Settings.Scope.GLOBAL) {
            stateLabel.setText("Global scope");
            return;
        }

        boolean inProject = scopeRunConfigurations.containsKey(normalizedKey);
        boolean inGlobal = globalRunConfigurations.containsKey(normalizedKey);

        if (inProject && inGlobal) {
            stateLabel.setText("Overrides Global");
        } else if (inProject) {
            stateLabel.setText("Project-only");
        } else if (inGlobal) {
            stateLabel.setText("Inherited from Global");
        } else {
            stateLabel.setText("Project-only");
        }
    }

    private static void clearRunConfigurationEditor(
            TextField keyField,
            TextArea preCommandsArea,
            TextArea commandsArea,
            TextArea postCommandsArea,
            Label stateLabel) {
        keyField.clear();
        preCommandsArea.clear();
        commandsArea.clear();
        postCommandsArea.clear();
        stateLabel.setText("No configuration selected");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static TextArea createOutputArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(2);
        area.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        return area;
    }

    public void show() {
        stage.show();
    }

    public Stage getStage() {
        return stage;
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

    // Probes a command by stripping --stdio (if present) and appending --version.
    private void testCommand(TextField field, TextArea output, Path workingDirectory) {
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
                if (workingDirectory != null) {
                    pb.directory(workingDirectory.toFile());
                }
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

    private static List<String> parseCommandLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private static String joinCommandLines(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        return String.join("\n", commands);
    }
}

