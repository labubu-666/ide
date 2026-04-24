package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

class CssStylerTest {

    @Test
    void selectorHighlighting() {
        String text = ".class { color: red; }";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();

        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void propertyHighlighting() {
        String text = "body { color: blue; }";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void atRuleHighlighting() {
        String text = "@import url('style.css');";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void hexColorHighlighting() {
        String text = "#fff { background: #ff0000; }";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void commentHighlighting() {
        String text = "/* css comment */";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void emptyText() {
        CssStyler styler = new CssStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isEqualTo(0);
    }

    @Test
    void numberWithUnitHighlighting() {
        String text = "div { width: 100px; }";
        CssStyler styler = new CssStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }
}
