package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownStylerTest {

    @Test
    void emptyText() {
        MarkdownStyler styler = new MarkdownStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(0, spans.length());
    }

    @Test
    void headingHighlighting() {
        String text = "# Hello World";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void h2HeadingHighlighting() {
        String text = "## Section Title";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
    }

    @Test
    void boldHighlighting() {
        String text = "This is **bold** text";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void italicHighlighting() {
        String text = "This is *italic* text";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void inlineCodeHighlighting() {
        String text = "Use `System.out.println()` to print";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void codeBlockHighlighting() {
        String text = "```\nint x = 1;\n```";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void linkHighlighting() {
        String text = "Visit [OpenAI](https://openai.com) for more";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void imageHighlighting() {
        String text = "![alt text](image.png)";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void blockquoteHighlighting() {
        String text = "> This is a blockquote";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void unorderedListItemHighlighting() {
        String text = "- item one\n- item two";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void orderedListItemHighlighting() {
        String text = "1. First\n2. Second";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
        assertTrue(spans.getSpanCount() > 0);
    }

    @Test
    void plainTextProducesNoHighlights() {
        String text = "just plain text here";
        MarkdownStyler styler = new MarkdownStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertNotNull(spans);
        assertEquals(text.length(), spans.length());
    }
}
