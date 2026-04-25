package settings;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import language.Language;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Settings {

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

    private static Path settingsFile;
    private static final Map<SettingKey, String> settings = new EnumMap<>(SettingKey.class);

    static {
        // Initialize with defaults
        for (SettingKey key : SettingKey.values()) {
            settings.put(key, key.getDefaultValue());
        }
    }

    private Settings() {}

    public static void load(Path rootPath) {
        settingsFile = rootPath.resolve(".ide/settings.json5");
        
        // Reset to defaults
        for (SettingKey key : SettingKey.values()) {
            settings.put(key, key.getDefaultValue());
        }

        if (!Files.exists(settingsFile)) return;

        try (Reader reader = Files.newBufferedReader(settingsFile)) {
            Json5Element element = JSON5.parse(reader);
            if (element == null || !element.isJson5Object()) return;
            Json5Object root = element.getAsJson5Object();

            if (!root.has("project")) return;
            Json5Object project = root.getAsJson5Object("project");
            if (project == null || !project.has("sdk")) return;
            Json5Object sdk = project.getAsJson5Object("sdk");
            if (sdk == null) return;

            // Load all settings from SDK
            for (SettingKey key : SettingKey.values()) {
                if (sdk.has(key.getSection())) {
                    Json5Object section = sdk.getAsJson5Object(key.getSection());
                    if (section != null && section.has(key.getKey())) {
                        String value = section.get(key.getKey()).getAsString();
                        if (value != null && !value.isBlank()) {
                            settings.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // leave defaults on any IO or parse error
        }
    }

    private static void save() {
        if (settingsFile == null) return;
        try {
            Files.createDirectories(settingsFile.getParent());
            
            // Build nested SDK structure with sections
            Map<String, Json5Object> sections = new java.util.HashMap<>();
            for (SettingKey key : SettingKey.values()) {
                String section = key.getSection();
                Json5Object sectionObj = sections.computeIfAbsent(section, s -> new Json5Object());
                sectionObj.addProperty(key.getKey(), settings.get(key));
            }
            
            Json5Object sdk = new Json5Object();
            for (Map.Entry<String, Json5Object> entry : sections.entrySet()) {
                sdk.add(entry.getKey(), entry.getValue());
            }
            
            Json5Object project = new Json5Object();
            project.add("sdk", sdk);
            Json5Object obj = new Json5Object();
            obj.addProperty("schema_version", "1.0.0");
            obj.add("project", project);
            
            try (Writer writer = Files.newBufferedWriter(settingsFile)) {
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

    /**
     * Set a setting value by key. Blank/null values fall back to defaults.
     *
     * @param key the setting key
     * @param value the value to set (can be null or blank)
     */
    public static void set(SettingKey key, String value) {
        String newValue = (value == null || value.isBlank()) ? key.getDefaultValue() : value.strip();
        settings.put(key, newValue);
        save();
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

}
