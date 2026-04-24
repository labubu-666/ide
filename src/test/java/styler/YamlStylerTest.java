package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

class YamlStylerTest {

    @Test
    void keyValueHighlighting() {
        String text = "name: value";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();

        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
        assertThat(spans.length()).isEqualTo(text.length());
    }

    @Test
    void emptyText() {
        YamlStyler styler = new YamlStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isEqualTo(0);
    }

    @Test
    void stringLiteralHighlighting() {
        String text = "name: \"hello world\"";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void commentHighlighting() {
        String text = "# this is a comment";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void listHighlighting() {
        String text = "  - item1\n  - item2";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void booleanKeywordHighlighting() {
        String text = "enabled: true";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void nullKeywordHighlighting() {
        String text = "value: null";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void numberHighlighting() {
        String text = "port: 8080";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void anchorAndAliasHighlighting() {
        String text = "default: &default\n  ref: *default";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void documentMarkerHighlighting() {
        String text = "---\nkey: value\n...";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void tagHighlighting() {
        String text = "date: !!timestamp 2024-01-01";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void multilineStringHighlighting() {
        String text = "message: |\n  Line one\n  Line two";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void foldHighlighting() {
        String text = "message: >\n  This is a\n  folded string";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void nestedStructureHighlighting() {
        String text = "person:\n  name: John\n  age: 30";
        YamlStyler styler = new YamlStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }
}
