package styler;

import org.junit.jupiter.api.Test;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

class JavaStylerTest {

    @Test
    void keywordHighlighting() {
        String text = "public class HelloWorld {";
        JavaStyler styler = new JavaStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();

        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
        assertThat(spans.length()).isEqualTo(text.length());
    }

    @Test
    void emptyText() {
        JavaStyler styler = new JavaStyler("");
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isEqualTo(0);
    }

    @Test
    void stringLiteralHighlighting() {
        String text = "String s = \"hello\";";
        JavaStyler styler = new JavaStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void commentHighlighting() {
        String text = "// this is a comment";
        JavaStyler styler = new JavaStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void multiLineCommentHighlighting() {
        String text = "/* multi-line\ncomment */";
        JavaStyler styler = new JavaStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }

    @Test
    void numberHighlighting() {
        String text = "int x = 42;";
        JavaStyler styler = new JavaStyler(text);
        StyleSpans<Collection<String>> spans = styler.style();
        assertThat(spans).isNotNull();
        assertThat(spans.length()).isGreaterThan(0);
    }
}
