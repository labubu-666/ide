package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BaseStylerTest {

    @Test
    void concreteSubclassWorks() {
        BaseStyler styler = new BaseStyler("hello") {
            @Override
            protected Pattern getPattern() {
                return Pattern.compile("(?<WORD>\\w+)");
            }

            @Override
            protected String getStyleClass(Matcher matcher) {
                if (matcher.group("WORD") != null) return "word";
                return null;
            }
        };

        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void emptyInputReturnsEmptySpans() {
        BaseStyler styler = new BaseStyler("") {
            @Override
            protected Pattern getPattern() {
                return Pattern.compile("(?<WORD>\\w+)");
            }

            @Override
            protected String getStyleClass(Matcher matcher) {
                return null;
            }
        };

        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(0, spans.length());
    }
}
