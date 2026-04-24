package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeScriptStyler extends BaseStyler {

    private static final String[] KEYWORDS = {
        // JavaScript keywords
        "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "function", "if", "import", "in", "instanceof", "let", "new",
        "of", "return", "super", "switch", "this", "throw", "try", "typeof",
        "var", "void", "while", "with", "yield",
        // TypeScript-specific
        "abstract", "as", "asserts", "async", "await", "declare", "enum",
        "from", "implements", "interface", "is", "keyof", "module", "namespace",
        "never", "readonly", "satisfies", "type", "undefined", "unique"
    };

    private static final String KEYWORD_PATTERN   = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String DECORATOR_PATTERN = "@[\\w.]+";
    private static final String STRING_PATTERN    = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`";
    private static final String COMMENT_PATTERN   = "//[^\\n]*|/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN    = "\\b\\d+(\\.\\d+)?\\b";
    private static final String PAREN_PATTERN     = "\\(|\\)";
    private static final String BRACE_PATTERN     = "\\{|\\}";
    private static final String BRACKET_PATTERN   = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = ";";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<COMMENT>"   + COMMENT_PATTERN   + ")"
        + "|(?<STRING>"    + STRING_PATTERN    + ")"
        + "|(?<DECORATOR>" + DECORATOR_PATTERN + ")"
        + "|(?<KEYWORD>"   + KEYWORD_PATTERN   + ")"
        + "|(?<NUMBER>"    + NUMBER_PATTERN    + ")"
        + "|(?<PAREN>"     + PAREN_PATTERN     + ")"
        + "|(?<BRACE>"     + BRACE_PATTERN     + ")"
        + "|(?<BRACKET>"   + BRACKET_PATTERN   + ")"
        + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
    );

    public TypeScriptStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        if (matcher.group("COMMENT")   != null) return "comment";
        if (matcher.group("STRING")    != null) return "string";
        if (matcher.group("DECORATOR") != null) return "decorator";
        if (matcher.group("KEYWORD")   != null) return "keyword";
        if (matcher.group("NUMBER")    != null) return "number";
        if (matcher.group("PAREN")     != null) return "paren";
        if (matcher.group("BRACE")     != null) return "brace";
        if (matcher.group("BRACKET")   != null) return "bracket";
        if (matcher.group("SEMICOLON") != null) return "semicolon";
        return null;
    }
}
