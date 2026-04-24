package filetype;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeRegistryTest {

    private FileTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FileTypeRegistry();
    }

    @Test
    void knownExtensionsResolveCorrectly() {
        assertEquals("Java",       registry.forExtension("java").name());
        assertEquals("Python",     registry.forExtension("py").name());
        assertEquals("CSS",        registry.forExtension("css").name());
        assertEquals("JavaScript", registry.forExtension("js").name());
        assertEquals("TypeScript", registry.forExtension("ts").name());
        assertEquals("Markdown",   registry.forExtension("md").name());
        assertEquals("Markdown",   registry.forExtension("markdown").name());
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertEquals("Java",   registry.forExtension("JAVA").name());
        assertEquals("Python", registry.forExtension("PY").name());
        assertEquals("CSS",    registry.forExtension("CSS").name());
    }

    @Test
    void unknownExtensionReturnsUnknown() {
        FileType result = registry.forExtension("xyz");
        assertSame(FileTypeRegistry.UNKNOWN, result);
        assertEquals("unknown", result.name());
    }

    @Test
    void nullExtensionReturnsUnknown() {
        assertSame(FileTypeRegistry.UNKNOWN, registry.forExtension(null));
    }

    @Test
    void emptyExtensionReturnsUnknown() {
        assertSame(FileTypeRegistry.UNKNOWN, registry.forExtension(""));
    }

    @Test
    void forPathExtractsExtensionCorrectly() {
        assertEquals("Java",   registry.forPath(Path.of("Main.java")).name());
        assertEquals("Python", registry.forPath(Path.of("script.py")).name());
        assertEquals("CSS",    registry.forPath(Path.of("style.css")).name());
    }

    @Test
    void forPathWithNoExtensionReturnsUnknown() {
        assertSame(FileTypeRegistry.UNKNOWN, registry.forPath(Path.of("Makefile")));
    }

    @Test
    void forPathWithNullReturnsUnknown() {
        assertSame(FileTypeRegistry.UNKNOWN, registry.forPath(null));
    }

    @Test
    void forPathNestedPathUsesFilename() {
        assertEquals("Java", registry.forPath(Path.of("src/main/java/Foo.java")).name());
    }

    @Test
    void unknownHasNoExtensions() {
        assertEquals(List.of(), FileTypeRegistry.UNKNOWN.extensions());
    }

    @Test
    void knownTypesHaveNonEmptyExtensions() {
        assertFalse(registry.forExtension("java").extensions().isEmpty());
    }

    @Test
    void sameInstanceReturnedForSameExtension() {
        assertSame(registry.forExtension("java"), registry.forExtension("java"));
    }
}
