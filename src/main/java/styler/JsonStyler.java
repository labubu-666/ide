package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonStyler extends BaseStyler {

    private static final Pattern PATTERN = Pattern.compile(
        "//[^\\n]*"  // single-line comments
        + "|/\\*[\\s\\S]*?\\*/"  // multi-line comments
        + "|\"(?:\\\\.|[^\"])*\""  // double-quoted strings
        + "|'(?:\\\\.|[^'])*'"  // single-quoted strings (JSON5)
        + "|(?:true|false|null|Infinity|NaN)"  // keywords
        + "|[+-]?(?:Infinity|NaN|0[xX][0-9a-fA-F]+|0[oO]?[0-7]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)"  // numbers
        + "|[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*:)"  // unquoted keys
        + "|[\\{\\}]"  // braces
        + "|[\\[\\]]"  // brackets
        + "|:"  // colon
        + "|,"  // comma
    );

    public JsonStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        String match = matcher.group();
        
        if (match.startsWith("//") || match.startsWith("/*")) {
            return "comment";
        }
        if (match.startsWith("\"") || match.startsWith("'")) {
            return "string";
        }
        if (match.equals("true") || match.equals("false") || match.equals("null") 
            || match.equals("Infinity") || match.equals("NaN")) {
            return "keyword";
        }
        if (match.matches("[+-]?(?:Infinity|NaN|0[xX][0-9a-fA-F]+|0[oO]?[0-7]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")) {
            return "number";
        }
        if (match.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return "key";
        }
        if (match.matches("[\\{\\}]")) {
            return "brace";
        }
        if (match.matches("[\\[\\]]")) {
            return "bracket";
        }
        if (match.equals(":")) {
            return "colon";
        }
        if (match.equals(",")) {
            return "comma";
        }
        return null;
    }
}
