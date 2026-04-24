package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

class JsonStylerTest {

    @Test
    void keyValueHighlighting() {
        String text = "{\"name\": \"value\"}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();

        assertThat(spans).isNotNull();
        assertThat(spans.getSpanCount()).isGreaterThan(0);
        assertThat(spans.length()).isEqualTo(text.length());
    }

    @Test
    void emptyText() {
        JsonStyler styler = new JsonStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isEqualTo(0);
    }

    @Test
    void stringHighlighting() {
        String text = "{\"message\": \"hello world\"}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void numberHighlighting() {
        String text = "{\"count\": 42, \"price\": 19.99}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void booleanKeywordHighlighting() {
        String text = "{\"enabled\": true, \"disabled\": false}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void nullKeywordHighlighting() {
        String text = "{\"value\": null}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void arrayHighlighting() {
        String text = "{\"items\": [1, 2, 3]}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void nestedObjectHighlighting() {
        String text = "{\"person\": {\"name\": \"John\", \"age\": 30}}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void json5SingleQuotesHighlighting() {
        String text = "{'name': 'value'}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void json5UnquotedKeysHighlighting() {
        String text = "{name: 'value', age: 30}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void jsoncsingleLineCommentHighlighting() {
        String text = "{\"name\": \"value\" // this is a comment\n}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void jsoncMultiLineCommentHighlighting() {
        String text = "{/* comment */ \"name\": \"value\"}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void hexNumberHighlighting() {
        String text = "{\"hex\": 0xFF}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void scientificNotationHighlighting() {
        String text = "{\"value\": 1.5e-10}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void complexMixedHighlighting() {
        String text = "{\n  \"name\": \"test\",\n  \"enabled\": true,\n  \"count\": 42,\n  \"values\": [1, 2, 3],\n  \"nested\": {\"key\": \"value\"}\n}";
        JsonStyler styler = new JsonStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }
}
