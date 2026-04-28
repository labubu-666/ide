package language;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import settings.Settings;
import styler.CssStyler;
import styler.JavaStyler;
import styler.JsonStyler;
import styler.MarkdownStyler;
import styler.PythonStyler;
import styler.ScssStyler;
import styler.TypeScriptStyler;
import styler.YamlStyler;

public final class Languages {

    private static final String DEFAULT_ICON = "host/icons/document.png";

    public static final Language UNKNOWN = new Language(
        "unknown", List.of(), DEFAULT_ICON, "plaintext",
        null, null, "/host/keywords/java.css"
    );

    private static final List<Language> ALL = List.of(
        new Language(
            "Java", List.of("java"), DEFAULT_ICON, "java",
            null,
            text -> new JavaStyler(text).style(),
                "/host/keywords/java.css"
        ),
        new Language(
            "Python", List.of("py"), DEFAULT_ICON, "python",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.PYLSP_PATH).split("\\s+")),
            text -> new PythonStyler(text).style(),
                "/host/keywords/python.css"
        ),
        new Language(
            "CSS", List.of("css"), DEFAULT_ICON, "css",
            null,
            text -> new CssStyler(text).style(),
                "/host/keywords/css.css"
        ),
        new Language(
            "SCSS", List.of("scss"), DEFAULT_ICON, "scss",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.SASS_SERVER_PATH).split("\\s+")),
            text -> new ScssStyler(text).style(),
                "/host/keywords/scss.css"
        ),
        new Language(
            "JavaScript", List.of("js", "jsx", "cjs", "mjs"), DEFAULT_ICON, "javascript",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.TS_SERVER_PATH).split("\\s+")),
            text -> new TypeScriptStyler(text).style(),
                "/host/keywords/typescript.css"
        ),
        new Language(
            "TypeScript", List.of("ts", "tsx", "cts", "mts"), DEFAULT_ICON, "typescript",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.TS_SERVER_PATH).split("\\s+")),
            text -> new TypeScriptStyler(text).style(),
                "/host/keywords/typescript.css"
        ),
        new Language(
            "Markdown", List.of("md", "markdown"), DEFAULT_ICON, "markdown",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.MARKDOWN_SERVER_PATH).split("\\s+")),
            text -> new MarkdownStyler(text).style(),
                "/host/keywords/markdown.css"
        ),
        new Language(
            "YAML", List.of("yaml", "yml"), DEFAULT_ICON, "yaml",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.YAML_SERVER_PATH).split("\\s+")),
            text -> new YamlStyler(text).style(),
                "/host/keywords/yaml.css"
        ),
        new Language(
            "JSON", List.of("json"), DEFAULT_ICON, "json",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.JSON_SERVER_PATH).split("\\s+")),
            text -> new JsonStyler(text).style(),
                "/host/keywords/json.css"
        ),
        new Language(
            "JSON5", List.of("json5"), DEFAULT_ICON, "json5",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.JSON_SERVER_PATH).split("\\s+")),
            text -> new JsonStyler(text).style(),
                "/host/keywords/json5.css"
        ),
        new Language(
            "JSONC", List.of("jsonc"), DEFAULT_ICON, "jsonc",
            () -> Arrays.asList(Settings.get(Settings.SettingKey.JSON_SERVER_PATH).split("\\s+")),
            text -> new JsonStyler(text).style(),
                "/host/keywords/jsonc.css"
        )
    );

    private static final Map<String, Language> BY_EXTENSION;

    static {
        Map<String, Language> map = new HashMap<>();
        for (Language lang : ALL) {
            for (String ext : lang.extensions()) {
                map.put(ext.toLowerCase(Locale.ROOT), lang);
            }
        }
        BY_EXTENSION = Map.copyOf(map);
    }

    private Languages() {}

    public static List<Language> all() {
        return ALL;
    }

    public static Language forExtension(String ext) {
        if (ext == null || ext.isEmpty()) return UNKNOWN;
        return BY_EXTENSION.getOrDefault(ext.toLowerCase(Locale.ROOT), UNKNOWN);
    }
}
