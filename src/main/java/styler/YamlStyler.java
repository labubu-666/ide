package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlStyler extends BaseStyler {

    private static final String[] KEYWORDS = new String[] {
        "true", "false", "yes", "no", "on", "off",
        "null", "~"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String KEY_PATTERN = "^[ \\t]*([a-zA-Z0-9_.-]+|\"[^\"]*\"|'[^']*')(?=\\s*:)(?!\\s*\\w)";
    private static final String STRING_PATTERN = "\"([^\"\\\\\n]|\\\\.)*\"|'([^'\\\\\n]|\\\\.)*'";
    private static final String MULTILINE_STRING_PATTERN = "\\|[+-]?[0-9]*|>[+-]?[0-9]*";
    private static final String NUMBER_PATTERN = "\\b(0[xX][0-9a-fA-F]+|0[oO][0-7]+|0[bB][01]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b";
    private static final String COMMENT_PATTERN = "#[^\\n]*";
    private static final String ANCHOR_PATTERN = "&[a-zA-Z0-9_-]+";
    private static final String ALIAS_PATTERN = "\\*[a-zA-Z0-9_-]+";
    private static final String TAG_PATTERN = "!!?[a-zA-Z0-9_-]+";
    private static final String DOCUMENT_MARKER_PATTERN = "^---|^\\.\\.\\.";
    private static final String LIST_MARKER_PATTERN = "^[ \\t]*-(\\s|$)";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String BRACE_PATTERN = "\\{|\\}";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<DOCUMENT>" + DOCUMENT_MARKER_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<MULTILINE>" + MULTILINE_STRING_PATTERN + ")"
        + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
        + "|(?<ANCHOR>" + ANCHOR_PATTERN + ")"
        + "|(?<ALIAS>" + ALIAS_PATTERN + ")"
        + "|(?<TAG>" + TAG_PATTERN + ")"
        + "|(?<KEY>" + KEY_PATTERN + ")"
        + "|(?<LIST>" + LIST_MARKER_PATTERN + ")"
        + "|(?<PAREN>" + PAREN_PATTERN + ")"
        + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
    , Pattern.MULTILINE);

    public YamlStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        if (matcher.group("COMMENT") != null) return "comment";
        if (matcher.group("DOCUMENT") != null) return "document";
        if (matcher.group("STRING") != null) return "string";
        if (matcher.group("MULTILINE") != null) return "multiline";
        if (matcher.group("KEYWORD") != null) return "keyword";
        if (matcher.group("NUMBER") != null) return "number";
        if (matcher.group("ANCHOR") != null) return "anchor";
        if (matcher.group("ALIAS") != null) return "alias";
        if (matcher.group("TAG") != null) return "tag";
        if (matcher.group("KEY") != null) return "key";
        if (matcher.group("LIST") != null) return "list";
        if (matcher.group("PAREN") != null) return "paren";
        if (matcher.group("BRACKET") != null) return "bracket";
        if (matcher.group("BRACE") != null) return "brace";
        return null;
    }
}
