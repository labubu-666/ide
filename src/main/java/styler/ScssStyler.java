package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScssStyler extends BaseStyler {
    private static final String[] PROPERTIES = new String[] {
        "-fx-fill", "-fx-font-weight", "-fx-background-color", "-fx-background-radius",
        "-fx-border-color", "-fx-border-width", "-fx-padding", "-fx-spacing",
        "-fx-min-width", "-fx-pref-width", "-fx-max-width", "-fx-min-height",
        "-fx-pref-height", "-fx-max-height", "-fx-alignment", "-fx-text-fill",
        "-fx-font-size", "-fx-font-family", "-fx-cursor", "-fx-effect",
        "color", "background", "border", "margin", "padding", "width", "height",
        "font-family", "font-size", "font-weight", "display", "position", "top",
        "left", "right", "bottom", "float", "clear", "overflow", "visibility",
        "z-index", "opacity", "transform", "transition", "animation", "content"
    };

    private static final String[] AT_RULES = new String[] {
        "@import", "@media", "@font-face", "@keyframes", "@supports", "@charset",
        "@include", "@extend", "@mixin", "@function", "@return", "@if", "@else", "@for", "@each", "@while"
    };

    private static final String[] SCSS_KEYWORDS = new String[] {
        "null", "true", "false", "and", "or", "not"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", PROPERTIES) + ")\\b";
    private static final String AT_RULE_PATTERN = "(" + String.join("|", AT_RULES) + ")";
    private static final String SCSS_KEYWORD_PATTERN = "\\b(" + String.join("|", SCSS_KEYWORDS) + ")\\b";
    private static final String SELECTOR_PATTERN = "[.#]?[a-zA-Z_-][a-zA-Z0-9_-]*";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String COLON_PATTERN = "\\:";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "'([^'\\\\]|\\\\.)*'|\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//.*|/\\*[^\\n]*\\*/";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?(px|em|rem|%|pt|pc|in|cm|mm|ex|ch|vw|vh|vmin|vmax)?\\b";
    private static final String HEX_PATTERN = "#[a-fA-F0-9]{3,8}\\b";
    private static final String VARIABLE_PATTERN = "\\$[a-zA-Z_-][a-zA-Z0-9_-]*";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<SELECTOR>^\\s*" + SELECTOR_PATTERN + "\\s*(,\\s*" + SELECTOR_PATTERN + "\\s*)*)"
        + "|(?<ATRULE>" + AT_RULE_PATTERN + ")"
        + "|(?<SCSSKEYWORD>" + SCSS_KEYWORD_PATTERN + ")"
        + "|(?<VARIABLE>" + VARIABLE_PATTERN + ")"
        + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        + "|(?<COLON>" + COLON_PATTERN + ")"
        + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<HEX>" + HEX_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );

    public ScssStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        if (matcher.group("SELECTOR") != null) return "selector";
        if (matcher.group("ATRULE") != null) return "atrule";
        if (matcher.group("SCSSKEYWORD") != null) return "scsskeyword";
        if (matcher.group("VARIABLE") != null) return "variable";
        if (matcher.group("KEYWORD") != null) return "keyword";
        if (matcher.group("BRACE") != null) return "brace";
        if (matcher.group("COLON") != null) return "colon";
        if (matcher.group("SEMICOLON") != null) return "semicolon";
        if (matcher.group("STRING") != null) return "string";
        if (matcher.group("COMMENT") != null) return "comment";
        if (matcher.group("HEX") != null) return "hex";
        if (matcher.group("NUMBER") != null) return "number";
        return null;
    }
}
