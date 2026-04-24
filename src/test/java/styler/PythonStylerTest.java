package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class PythonStylerTest {

    @Test
    void keywordHighlighting() {
        String text = "def hello(): pass";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();

        assertNotNull(spans);
        assertTrue(spans.getSpanCount() > 0);
        assertEquals(text.length(), spans.length());
    }

    @Test
    void emptyText() {
        PythonStyler styler = new PythonStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(0, spans.length());
    }

    @Test
    void stringLiteralHighlighting() {
        String text = "s = \"hello\"";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void tripleQuotedStringHighlighting() {
        String text = "s = \"\"\"multi\nline\"\"\"";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void commentHighlighting() {
        String text = "# this is a comment";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void decoratorHighlighting() {
        String text = "@staticmethod\ndef foo(): pass";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void builtinHighlighting() {
        String text = "print(len([1, 2, 3]))";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void numberHighlighting() {
        String text = "x = 42";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }

    @Test
    void fStringHighlighting() {
        String text = "s = f\"hello {name}\"";
        PythonStyler styler = new PythonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertTrue(spans.length() > 0);
    }
}
