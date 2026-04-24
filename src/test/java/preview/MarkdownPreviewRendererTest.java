package preview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MarkdownPreviewRendererTest {

    private MarkdownPreviewRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownPreviewRenderer();
    }

    @Test
    void idIsMarkdown() {
        assertThat(renderer.id()).isEqualTo("markdown");
    }

    @Test
    void displayNameIsSet() {
        assertThat(renderer.displayName()).isNotNull();
        assertThat(renderer.displayName().isBlank()).isFalse();
    }

    @Test
    void supportsMd() {
        assertThat(renderer.supports("md")).isTrue();
    }

    @Test
    void supportsMarkdown() {
        assertThat(renderer.supports("markdown")).isTrue();
    }

    @Test
    void supportsCaseInsensitive() {
        assertThat(renderer.supports("MD")).isTrue();
        assertThat(renderer.supports("MARKDOWN")).isTrue();
        assertThat(renderer.supports("Md")).isTrue();
    }

    @Test
    void doesNotSupportOtherExtensions() {
        assertThat(renderer.supports("txt")).isFalse();
        assertThat(renderer.supports("java")).isFalse();
        assertThat(renderer.supports("html")).isFalse();
        assertThat(renderer.supports(null)).isFalse();
    }

    @Test
    void emptyInputProducesNonNullHtml() {
        String html = renderer.renderToHtml("");
        assertThat(html).isNotNull();
    }

    @Test
    void nullInputTreatedAsEmpty() {
        String html = renderer.renderToHtml(null);
        assertThat(html).isNotNull();
    }

    @Test
    void headingProducesH1Tag() {
        String html = renderer.renderToHtml("# Hello World");
        assertThat(html).as("Expected <h1> in: %s", html).contains("<h1>");
    }

    @Test
    void h2HeadingProducesH2Tag() {
        String html = renderer.renderToHtml("## Section");
        assertThat(html).as("Expected <h2> in: %s", html).contains("<h2>");
    }

    @Test
    void h6HeadingProducesH6Tag() {
        String html = renderer.renderToHtml("###### Deep");
        assertThat(html).as("Expected <h6> in: %s", html).contains("<h6>");
    }

    @Test
    void boldProducesStrongTag() {
        String html = renderer.renderToHtml("**bold text**");
        assertThat(html).as("Expected <strong> in: %s", html).contains("<strong>");
    }

    @Test
    void italicProducesEmTag() {
        String html = renderer.renderToHtml("*italic text*");
        assertThat(html).as("Expected <em> in: %s", html).contains("<em>");
    }

    @Test
    void inlineCodeProducesCodeTag() {
        String html = renderer.renderToHtml("Use `System.out.println()` here");
        assertThat(html).as("Expected <code> in: %s", html).contains("<code>");
    }

    @Test
    void fencedCodeBlockProducesPreAndCode() {
        String html = renderer.renderToHtml("```\nint x = 1;\n```");
        assertThat(html).as("Expected <pre> in: %s", html).contains("<pre>");
        assertThat(html).as("Expected <code> in: %s", html).contains("<code>");
    }

    @Test
    void linkProducesAnchorTag() {
        String html = renderer.renderToHtml("[OpenAI](https://openai.com)");
        assertThat(html).as("Expected <a tag in: %s", html).contains("<a ");
        assertThat(html).as("Expected URL in: %s", html).contains("openai.com");
    }

    @Test
    void imageProducesImgTag() {
        String html = renderer.renderToHtml("![alt text](image.png)");
        assertThat(html).as("Expected <img tag in: %s", html).contains("<img ");
    }

    @Test
    void blockquoteProducesBlockquoteTag() {
        String html = renderer.renderToHtml("> This is a quote");
        assertThat(html).as("Expected <blockquote> in: %s", html).contains("<blockquote>");
    }

    @Test
    void tableExtensionProducesTableTag() {
        String md = "| A | B |\n|---|---|\n| 1 | 2 |";
        String html = renderer.renderToHtml(md);
        assertThat(html).as("Expected <table> in: %s", html).contains("<table>");
    }

    @Test
    void strikethroughExtensionProducesDelTag() {
        String html = renderer.renderToHtml("~~strike~~");
        assertThat(html).as("Expected <del> or <s> in: %s", html)
            .satisfies(h -> org.assertj.core.api.Assertions.assertThat(h.contains("<del>") || h.contains("<s>")).isTrue());
    }

    @Test
    void taskListExtensionProducesCheckbox() {
        String html = renderer.renderToHtml("- [x] done\n- [ ] not done");
        assertThat(html).as("Expected checkbox input in: %s", html)
            .satisfies(h -> org.assertj.core.api.Assertions.assertThat(h.contains("checkbox") || h.contains("type=\"checkbox\"")).isTrue());
    }

    @Test
    void outputContainsHtmlBoilerplate() {
        String html = renderer.renderToHtml("# Test");
        assertThat(html).as("Expected DOCTYPE in: %s", html).contains("<!DOCTYPE html>");
        assertThat(html).as("Expected <body> in: %s", html).contains("<body>");
    }
}
