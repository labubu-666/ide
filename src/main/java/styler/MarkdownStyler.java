package styler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownStyler extends BaseStyler {
    private static final String HEADING_PATTERN   = "^#{1,6}[^\\n]*";
    private static final String BOLD_PATTERN       = "\\*\\*(.+?)\\*\\*|__(.+?)__";
    private static final String ITALIC_PATTERN     = "\\*(.+?)\\*|_(.+?)_";
    private static final String CODE_BLOCK_PATTERN = "```[\\s\\S]*?```";
    private static final String INLINE_CODE_PATTERN = "`[^`\\n]+`";
    private static final String LINK_PATTERN       = "\\[([^\\]]+)\\]\\([^)]+\\)";
    private static final String IMAGE_PATTERN      = "!\\[([^\\]]*?)\\]\\([^)]+\\)";
    private static final String BLOCKQUOTE_PATTERN = "^>[ \\t]?[^\\n]*";
    private static final String HR_PATTERN         = "^([-*_]){3,}\\s*$";
    private static final String LIST_ITEM_PATTERN  = "^[ \\t]*[-*+][ \\t]|^[ \\t]*\\d+\\.[ \\t]";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<CODEBLOCK>"   + CODE_BLOCK_PATTERN    + ")"
        + "|(?<HEADING>"  + HEADING_PATTERN        + ")"
        + "|(?<BOLD>"     + BOLD_PATTERN           + ")"
        + "|(?<ITALIC>"   + ITALIC_PATTERN         + ")"
        + "|(?<IMAGE>"    + IMAGE_PATTERN          + ")"
        + "|(?<LINK>"     + LINK_PATTERN           + ")"
        + "|(?<BLOCKQUOTE>" + BLOCKQUOTE_PATTERN   + ")"
        + "|(?<HR>"       + HR_PATTERN             + ")"
        + "|(?<LISTITEM>" + LIST_ITEM_PATTERN      + ")"
        + "|(?<INLINECODE>" + INLINE_CODE_PATTERN  + ")",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    public MarkdownStyler(String text) {
        super(text);
    }

    @Override
    protected Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getStyleClass(Matcher matcher) {
        if (matcher.group("CODEBLOCK")   != null) return "code-block";
        if (matcher.group("HEADING")     != null) return "heading";
        if (matcher.group("BOLD")        != null) return "bold";
        if (matcher.group("ITALIC")      != null) return "italic";
        if (matcher.group("IMAGE")       != null) return "image";
        if (matcher.group("LINK")        != null) return "link";
        if (matcher.group("BLOCKQUOTE")  != null) return "blockquote";
        if (matcher.group("HR")          != null) return "hr";
        if (matcher.group("LISTITEM")    != null) return "list-item";
        if (matcher.group("INLINECODE")  != null) return "inline-code";
        return null;
    }
}
