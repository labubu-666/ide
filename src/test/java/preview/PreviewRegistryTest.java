package preview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PreviewRegistryTest {

    private PreviewRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PreviewRegistry();
    }

    @Test
    void mdExtensionReturnsMarkdownRenderer() {
        Optional<PreviewRenderer> result = registry.forExtension("md");
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("markdown");
    }

    @Test
    void markdownExtensionReturnsMarkdownRenderer() {
        Optional<PreviewRenderer> result = registry.forExtension("markdown");
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("markdown");
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(registry.forExtension("MD")).isPresent();
        assertThat(registry.forExtension("MARKDOWN")).isPresent();
        assertThat(registry.forExtension("Md")).isPresent();
    }

    @Test
    void unknownExtensionReturnsEmpty() {
        assertThat(registry.forExtension("java")).isEmpty();
        assertThat(registry.forExtension("py")).isEmpty();
        assertThat(registry.forExtension("xyz")).isEmpty();
    }

    @Test
    void nullExtensionReturnsEmpty() {
        assertThat(registry.forExtension(null)).isEmpty();
    }

    @Test
    void emptyExtensionReturnsEmpty() {
        assertThat(registry.forExtension("")).isEmpty();
    }

    @Test
    void forPathExtractsExtensionCorrectly() {
        Optional<PreviewRenderer> result = registry.forPath(Path.of("README.md"));
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("markdown");
    }

    @Test
    void forPathMarkdownExtension() {
        Optional<PreviewRenderer> result = registry.forPath(Path.of("docs/guide.markdown"));
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("markdown");
    }

    @Test
    void forPathNestedPathUsesFilename() {
        Optional<PreviewRenderer> result = registry.forPath(Path.of("docs/api/README.md"));
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("markdown");
    }

    @Test
    void forPathNonPreviewableExtensionReturnsEmpty() {
        assertThat(registry.forPath(Path.of("Main.java"))).isEmpty();
    }

    @Test
    void forPathNullReturnsEmpty() {
        assertThat(registry.forPath(null)).isEmpty();
    }

    @Test
    void hasPreviewTrueForMd() {
        assertThat(registry.hasPreview("md")).isTrue();
        assertThat(registry.hasPreview("markdown")).isTrue();
    }

    @Test
    void hasPreviewFalseForUnknown() {
        assertThat(registry.hasPreview("java")).isFalse();
        assertThat(registry.hasPreview("xyz")).isFalse();
        assertThat(registry.hasPreview(null)).isFalse();
        assertThat(registry.hasPreview("")).isFalse();
    }
}
