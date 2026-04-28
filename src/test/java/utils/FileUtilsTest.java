package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileUtilsTest {

    @Test
    void testIsValidDirectory_withValidDirectory(@TempDir Path tempDir) {
        assertThat(FileUtils.isValidDirectory(tempDir)).isTrue();
    }

    @Test
    void testIsValidDirectory_withNullPath() {
        assertThat(FileUtils.isValidDirectory(null)).isFalse();
    }

    @Test
    void testIsValidDirectory_withNonExistentPath(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("nonexistent");
        assertThat(FileUtils.isValidDirectory(nonExistent)).isFalse();
    }

    @Test
    void testIsValidDirectory_withFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("testfile.txt");
        Files.createFile(file);
        assertThat(FileUtils.isValidDirectory(file)).isFalse();
    }

    @Test
    void testGetValidationError_withValidDirectory(@TempDir Path tempDir) {
        assertThat(FileUtils.getValidationError(tempDir)).isNull();
    }

    @Test
    void testGetValidationError_withNullPath() {
        String error = FileUtils.getValidationError(null);
        assertThat(error).isNotNull().contains("null");
    }

    @Test
    void testGetValidationError_withNonExistentPath(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("nonexistent");
        String error = FileUtils.getValidationError(nonExistent);
        assertThat(error).isNotNull()
            .contains("does not exist")
            .contains(nonExistent.toString());
    }

    @Test
    void testGetValidationError_withFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("testfile.txt");
        Files.createFile(file);
        String error = FileUtils.getValidationError(file);
        assertThat(error).isNotNull()
            .contains("not a directory")
            .contains(file.toString());
    }

    @Test
    void testGetValidationError_withNestedDirectory(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("level1/level2/level3");
        Files.createDirectories(nested);
        assertThat(FileUtils.getValidationError(nested)).isNull();
        assertThat(FileUtils.isValidDirectory(nested)).isTrue();
    }
}
