package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonStyler extends BaseStyler {
    private static final String[] KEYWORDS = new String[] {
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield", "match", "case"
    };

    private static final String[] BUILTINS = new String[] {
        "print", "len", "range", "int", "str", "float", "bool", "list",
        "dict", "tuple", "set", "input", "open", "type", "isinstance",
        "super", "self", "enumerate", "zip", "map", "filter", "sorted"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String BUILTIN_PATTERN = "\\b(" + String.join("|", BUILTINS) + ")\\b";
    private static final String DECORATOR_PATTERN = "@[a-zA-Z_][a-zA-Z0-9_]*";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String COLON_PATTERN = "\\:";
    private static final String STRING_PATTERN =
        "[rRbBuUfF]{0,2}\"\"\"(.|\\R)*?\"\"\""
        + "|[rRbBuUfF]{0,2}'''(.|\\R)*?'''"
        + "|[rRbBuUfF]{0,2}\"([^\"\\\\\\n]|\\\\.)*\""
        + "|[rRbBuUfF]{0,2}'([^'\\\\\\n]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "#[^\\n]*";
    private static final String NUMBER_PATTERN =
        "\\b(0[xX][0-9a-fA-F]+|0[oO][0-7]+|0[bB][01]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?[jJ]?)\\b";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<DECORATOR>" + DECORATOR_PATTERN + ")"
        + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<BUILTIN>" + BUILTIN_PATTERN + ")"
        + "|(?<PAREN>" + PAREN_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        + "|(?<COLON>" + COLON_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );

    public PythonStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        if (matcher.group("COMMENT") != null) return "comment";
        if (matcher.group("STRING") != null) return "string";
        if (matcher.group("DECORATOR") != null) return "decorator";
        if (matcher.group("KEYWORD") != null) return "keyword";
        if (matcher.group("BUILTIN") != null) return "builtin";
        if (matcher.group("PAREN") != null) return "paren";
        if (matcher.group("BRACE") != null) return "brace";
        if (matcher.group("BRACKET") != null) return "bracket";
        if (matcher.group("COLON") != null) return "colon";
        if (matcher.group("NUMBER") != null) return "number";
        return null;
    }
}
