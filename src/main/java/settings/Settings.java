package settings;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import language.Language;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Settings {

    public enum Scope {
        GLOBAL,
        PROJECT
    }

    public static final class RunConfiguration {
        private final List<String> preCommands;
        private final List<String> commands;
        private final List<String> postCommands;

        public RunConfiguration(List<String> preCommands, List<String> commands, List<String> postCommands) {
            this.preCommands = normalizeCommands(preCommands);
            this.commands = normalizeCommands(commands);
            this.postCommands = normalizeCommands(postCommands);
        }

        public List<String> preCommands() {
            return preCommands;
        }

        public List<String> commands() {
            return commands;
        }

        public List<String> postCommands() {
            return postCommands;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RunConfiguration that)) return false;
            return Objects.equals(preCommands, that.preCommands)
                    && Objects.equals(commands, that.commands)
                    && Objects.equals(postCommands, that.postCommands);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preCommands, commands, postCommands);
        }
    }

    /**
     * Enum representing all available settings with their default values,
     * JSON paths, and metadata for configuration management.
     */
    public enum SettingKey {
        PYTHON_INTERPRETER("python", "interpreter", "python3"),
        PYLSP_PATH("python", "pylspPath", "pylsp"),
        TS_SERVER_PATH("typescript", "serverPath", "typescript-language-server --stdio"),
        SASS_SERVER_PATH("sass", "serverPath", "some-sass-language-server --stdio"),
        YAML_SERVER_PATH("yaml", "serverPath", "yaml-language-server --stdio"),
        JSON_SERVER_PATH("json", "serverPath", "vscode-json-languageserver --stdio"),
        MARKDOWN_SERVER_PATH("markdown", "serverPath", "rumdl server");

        private final String section;
        private final String key;
        private final String defaultValue;

        SettingKey(String section, String key, String defaultValue) {
            this.section = section;
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public String getSection() {
            return section;
        }

        public String getKey() {
            return key;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }

    private static final Json5 JSON5 = new Json5();

    private static Path globalSettingsFile;
    private static Path projectSettingsFile;
    private static final Map<SettingKey, String> settings = new EnumMap<>(SettingKey.class);
    private static final Map<SettingKey, String> globalSettings = new EnumMap<>(SettingKey.class);
    private static final Map<SettingKey, String> projectSettings = new EnumMap<>(SettingKey.class);
    private static final Map<String, RunConfiguration> runConfigurations = new LinkedHashMap<>();
    private static final Map<String, RunConfiguration> globalRunConfigurations = new LinkedHashMap<>();
    private static final Map<String, RunConfiguration> projectRunConfigurations = new LinkedHashMap<>();
    private static final Set<String> runConfigurationCollisionKeys = new LinkedHashSet<>();

    static {
        // Initialize with defaults
        for (SettingKey key : SettingKey.values()) {
            settings.put(key, key.getDefaultValue());
            globalSettings.put(key, key.getDefaultValue());
        }
    }

    private Settings() {}

    public static void load(Path rootPath) {
        String userHome = System.getProperty("user.home", "").strip();
        globalSettingsFile = Path.of(userHome).resolve(".ide/settings.json5");
        projectSettingsFile = rootPath.resolve(".ide/settings.json5");

        for (SettingKey key : SettingKey.values()) {
            String defaultValue = key.getDefaultValue();
            globalSettings.put(key, defaultValue);
            settings.put(key, defaultValue);
        }

        projectSettings.clear();
        runConfigurations.clear();
        globalRunConfigurations.clear();
        projectRunConfigurations.clear();
        runConfigurationCollisionKeys.clear();

        loadScope(globalSettingsFile, globalSettings, globalRunConfigurations);
        loadScope(projectSettingsFile, projectSettings, projectRunConfigurations);

        recomputeEffectiveSettings();
    }

    private static void loadScope(
            Path file,
            Map<SettingKey, String> sdkTarget,
            Map<String, RunConfiguration> runConfigurationsTarget) {
        if (file == null || !Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            Json5Element element = JSON5.parse(reader);
            if (element == null || !element.isJson5Object()) return;
            Json5Object root = element.getAsJson5Object();
            Json5Object project = getObject(root, "project");
            if (project == null) return;

            Json5Object sdk = getObject(project, "sdk");
            if (sdk != null) {
                for (SettingKey key : SettingKey.values()) {
                    Json5Object section = getObject(sdk, key.getSection());
                    if (section != null && section.has(key.getKey())) {
                        Json5Element valueElement = section.get(key.getKey());
                        if (valueElement != null && valueElement.isJson5Primitive()) {
                            String value = valueElement.getAsString();
                            if (value != null && !value.isBlank()) {
                                sdkTarget.put(key, value.strip());
                            }
                        }
                    }
                }
            }

            Json5Object runConfigs = getObject(project, "runConfigurations");
            if (runConfigs != null) {
                for (Map.Entry<String, Json5Element> entry : runConfigs.entrySet()) {
                    String key = entry.getKey();
                    Json5Element value = entry.getValue();
                    if (key == null || key.isBlank() || value == null || !value.isJson5Object()) {
                        continue;
                    }
                    Json5Object runConfig = value.getAsJson5Object();
                    List<String> preCommands = readCommandArray(runConfig, "preCommands");
                    List<String> commands = readCommandArray(runConfig, "commands");
                    List<String> postCommands = readCommandArray(runConfig, "postCommands");
                    if (preCommands == null || commands == null || postCommands == null) {
                        continue;
                    }
                    runConfigurationsTarget.put(key.strip(), new RunConfiguration(preCommands, commands, postCommands));
                }
            }
        } catch (Exception e) {
            // keep current values on parse/load errors
        }
    }

    private static void save(Scope scope) {
        Path targetFile = scope == Scope.GLOBAL ? globalSettingsFile : projectSettingsFile;
        if (targetFile == null) return;

        Map<SettingKey, String> sourceSettings = scope == Scope.GLOBAL ? globalSettings : projectSettings;
        Map<String, RunConfiguration> sourceRunConfigurations =
                scope == Scope.GLOBAL ? globalRunConfigurations : projectRunConfigurations;

        try {
            Files.createDirectories(targetFile.getParent());
            
            // Build nested SDK structure with sections
            Map<String, Json5Object> sections = new HashMap<>();
            for (Map.Entry<SettingKey, String> entry : sourceSettings.entrySet()) {
                SettingKey key = entry.getKey();
                String section = key.getSection();
                Json5Object sectionObj = sections.computeIfAbsent(section, s -> new Json5Object());
                sectionObj.addProperty(key.getKey(), entry.getValue());
            }
            
            Json5Object sdk = new Json5Object();
            for (Map.Entry<String, Json5Object> entry : sections.entrySet()) {
                sdk.add(entry.getKey(), entry.getValue());
            }

            Json5Object runConfigs = new Json5Object();
            for (Map.Entry<String, RunConfiguration> entry : sourceRunConfigurations.entrySet()) {
                RunConfiguration runConfig = entry.getValue();
                Json5Object configObj = new Json5Object();
                configObj.add("preCommands", toJsonArray(runConfig.preCommands()));
                configObj.add("commands", toJsonArray(runConfig.commands()));
                configObj.add("postCommands", toJsonArray(runConfig.postCommands()));
                runConfigs.add(entry.getKey(), configObj);
            }
            
            Json5Object project = new Json5Object();
            project.add("sdk", sdk);
            project.add("runConfigurations", runConfigs);
            Json5Object obj = new Json5Object();
            obj.addProperty("schema_version", "1.0.0");
            obj.add("project", project);
            
            try (Writer writer = Files.newBufferedWriter(targetFile)) {
                writer.write(JSON5.serialize(obj));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Get a setting value by key.
     *
     * @param key the setting key
     * @return the current value or default if not set
     */
    public static String get(SettingKey key) {
        return settings.get(key);
    }

    public static String get(Scope scope, SettingKey key) {
        if (scope == Scope.GLOBAL) {
            return globalSettings.get(key);
        }
        return projectSettings.getOrDefault(key, globalSettings.get(key));
    }

    /**
     * Set a setting value by key. Blank/null values fall back to defaults.
     *
     * @param key the setting key
     * @param value the value to set (can be null or blank)
     */
    public static void set(SettingKey key, String value) {
        set(Scope.PROJECT, key, value);
    }

    public static void set(Scope scope, SettingKey key, String value) {
        String normalized = value == null ? "" : value.strip();

        if (scope == Scope.GLOBAL) {
            String newValue = normalized.isEmpty() ? key.getDefaultValue() : normalized;
            globalSettings.put(key, newValue);
            save(Scope.GLOBAL);
        } else {
            if (normalized.isEmpty()) {
                projectSettings.remove(key);
            } else {
                projectSettings.put(key, normalized);
            }
            save(Scope.PROJECT);
        }

        recomputeEffectiveSettings();
    }

    public static Map<String, RunConfiguration> getRunConfigurations() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(runConfigurations));
    }

    public static Map<String, RunConfiguration> getRunConfigurations(Scope scope) {
        Map<String, RunConfiguration> source =
                scope == Scope.GLOBAL ? globalRunConfigurations : projectRunConfigurations;
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static Set<String> getRunConfigurationCollisionKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(runConfigurationCollisionKeys));
    }

    public static void setRunConfigurations(Map<String, RunConfiguration> configurations) {
        setRunConfigurations(Scope.PROJECT, configurations);
    }

    public static void setRunConfigurations(Scope scope, Map<String, RunConfiguration> configurations) {
        Map<String, RunConfiguration> target =
                scope == Scope.GLOBAL ? globalRunConfigurations : projectRunConfigurations;
        target.clear();

        if (configurations != null) {
            for (Map.Entry<String, RunConfiguration> entry : configurations.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().strip();
                if (key.isBlank()) {
                    continue;
                }
                RunConfiguration value = entry.getValue();
                RunConfiguration normalized = value == null
                        ? new RunConfiguration(List.of(), List.of(), List.of())
                        : new RunConfiguration(value.preCommands(), value.commands(), value.postCommands());
                target.put(key, normalized);
            }
        }

        save(scope);
        recomputeEffectiveSettings();
    }

    private static void recomputeEffectiveSettings() {
        for (SettingKey key : SettingKey.values()) {
            String globalValue = globalSettings.getOrDefault(key, key.getDefaultValue());
            settings.put(key, projectSettings.getOrDefault(key, globalValue));
        }

        runConfigurations.clear();
        runConfigurations.putAll(globalRunConfigurations);
        runConfigurationCollisionKeys.clear();

        for (Map.Entry<String, RunConfiguration> entry : projectRunConfigurations.entrySet()) {
            String key = entry.getKey();
            if (runConfigurations.containsKey(key)) {
                runConfigurationCollisionKeys.add(key);
            }
            runConfigurations.put(key, entry.getValue());
        }
    }

    /**
     * Get the LSP command for a given language.
     * Returns a list of command parts ready for ProcessBuilder, or empty list if not supported.
     *
     * @param language the language
     * @return list of command parts, or empty list if language doesn't support LSP
     */
    public static List<String> getLspCommand(Language language) {
        SettingKey settingKey = switch (language.languageId()) {
            case "python" -> SettingKey.PYLSP_PATH;
            case "typescript", "javascript" -> SettingKey.TS_SERVER_PATH;
            case "scss" -> SettingKey.SASS_SERVER_PATH;
            case "yaml" -> SettingKey.YAML_SERVER_PATH;
            case "json", "json5", "jsonc" -> SettingKey.JSON_SERVER_PATH;
            case "markdown" -> SettingKey.MARKDOWN_SERVER_PATH;
            default -> null;
        };
        
        if (settingKey == null) {
            return List.of();
        }
        
        String command = get(settingKey);
        return Arrays.asList(command.split("\\s+"));
    }

    private static Json5Object getObject(Json5Object parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        Json5Element element = parent.get(key);
        if (element == null || !element.isJson5Object()) {
            return null;
        }
        return element.getAsJson5Object();
    }

    private static List<String> readCommandArray(Json5Object object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        Json5Element element = object.get(key);
        if (element == null || !element.isJson5Array()) {
            return null;
        }
        Json5Array jsonArray = element.getAsJson5Array();
        java.util.ArrayList<String> commands = new java.util.ArrayList<>();
        for (Json5Element commandElement : jsonArray) {
            if (commandElement == null || !commandElement.isJson5Primitive()) {
                return null;
            }
            String value = commandElement.getAsString();
            if (value != null && !value.isBlank()) {
                commands.add(value.strip());
            }
        }
        return List.copyOf(commands);
    }

    private static Json5Array toJsonArray(List<String> commands) {
        Json5Array array = new Json5Array();
        for (String command : normalizeCommands(commands)) {
            array.add(command);
        }
        return array;
    }

    private static List<String> normalizeCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> normalized = new java.util.ArrayList<>();
        for (String command : commands) {
            if (command == null) {
                continue;
            }
            String value = command.strip();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

}
