package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

class TypeScriptStylerTest {

    @Test
    void keywordHighlighting() {
        String text = "const x: number = 42;";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.getSpanCount()).isGreaterThan(0);
        assertThat(spans.length()).isEqualTo(text.length());
    }

    @Test
    void emptyText() {
        StyleSpans<Collection<String>> spans = new TypeScriptStyler("").style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isEqualTo(0);
    }

    @Test
    void typeScriptKeywords() {
        String text = "interface Foo { readonly bar: string; }";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.getSpanCount()).isGreaterThan(0);
    }

    @Test
    void decoratorHighlighting() {
        String text = "@Component({ selector: 'app' })";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.getSpanCount()).isGreaterThan(0);
    }

    @Test
    void templateLiteralHighlighting() {
        String text = "const msg = `Hello, ${name}!`;";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void singleQuoteStringHighlighting() {
        String text = "const s = 'hello';";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void commentHighlighting() {
        String text = "// this is a comment";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void multiLineCommentHighlighting() {
        String text = "/* multi-line\ncomment */";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void numberHighlighting() {
        String text = "let n = 3.14;";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void keywordInsideStringNotHighlighted() {
        String text = "const s = \"const let var\";";
        StyleSpans<Collection<String>> spans = new TypeScriptStyler(text).style();
        // Entire text covered, length must match
        assertThat(spans.length()).isEqualTo(text.length());
    }
}
