package styler;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

public abstract class BaseStyler {
    protected final String text;

    protected BaseStyler(String text) {
        this.text = text;
    }

    protected abstract Pattern getPattern();

    protected abstract String getStyleClass(Matcher matcher);

    public StyleSpans<Collection<String>> style() {
        Matcher matcher = getPattern().matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singletonList(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
