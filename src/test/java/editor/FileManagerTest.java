package editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    @TempDir
    Path tempDir;

    private final FileManager fileManager = new FileManager();

    @Test
    void testLoadFile() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.writeString(testFile, content);

        // Load the file
        FileManager.FileContent result = fileManager.loadFile(testFile);

        assertThat(result.getContent()).isEqualTo(content);
        assertThat(result.getChecksum()).isNotNull();
        assertThat(result.getLastModifiedTime()).isGreaterThan(0);
    }

    @Test
    void testLoadNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        assertThatThrownBy(() -> fileManager.loadFile(nonExistentFile)).isInstanceOf(IOException.class);
    }

    @Test
    void testIsModifiedWithSameContent() throws IOException {
        String content = "Test content";
        String checksum = fileManager.calculateChecksum(content);

        assertThat(fileManager.isModified(content, checksum)).isFalse();
    }

    @Test
    void testIsModifiedWithDifferentContent() throws IOException {
        String originalContent = "Original content";
        String checksum = fileManager.calculateChecksum(originalContent);
        String modifiedContent = "Modified content";

        assertThat(fileManager.isModified(modifiedContent, checksum)).isTrue();
    }

    @Test
    void testIsModifiedWithNullChecksum() {
        // New/unsaved files should be considered modified if they have content
        assertThat(fileManager.isModified("Some content", null)).isTrue();
        assertThat(fileManager.isModified("", null)).isFalse();
    }

    @Test
    void testSaveFile() throws IOException, FileManager.OptimisticLockException {
        Path testFile = tempDir.resolve("save_test.txt");
        String content = "Content to save";
        long initialTime = System.currentTimeMillis();

        // Save the file
        fileManager.saveFile(testFile, content, 0); // 0 for new file

        // Verify the file was saved
        assertThat(Files.exists(testFile)).isTrue();
        assertThat(Files.readString(testFile)).isEqualTo(content);
        assertThat(Files.getLastModifiedTime(testFile).toMillis()).isGreaterThanOrEqualTo(initialTime);
    }

    @Test
    void testSaveFileWithOptimisticLockSuccess() throws IOException, FileManager.OptimisticLockException {
        Path testFile = tempDir.resolve("lock_test.txt");
        String originalContent = "Original";
        Files.writeString(testFile, originalContent);
        long lastModifiedTime = Files.getLastModifiedTime(testFile).toMillis();

        String newContent = "Modified";
        fileManager.saveFile(testFile, newContent, lastModifiedTime);

        assertThat(Files.readString(testFile)).isEqualTo(newContent);
    }

    @Test
    void testSaveFileWithOptimisticLockFailure() throws IOException, InterruptedException {
        Path testFile = tempDir.resolve("lock_fail_test.txt");
        String originalContent = "Original";
        Files.writeString(testFile, originalContent);
        long lastModifiedTime = Files.getLastModifiedTime(testFile).toMillis();

        // Wait to ensure file modification time changes (1+ second precision on macOS)
        Thread.sleep(1100);

        // Simulate external modification
        Files.writeString(testFile, "Externally modified");

        String newContent = "Modified";
        assertThatThrownBy(() -> fileManager.saveFile(testFile, newContent, lastModifiedTime))
            .isInstanceOf(FileManager.OptimisticLockException.class)
            .hasMessageContaining("modified externally");
        // File should still contain the external modification
        assertThat(Files.readString(testFile)).isEqualTo("Externally modified");
    }

    @Test
    void testChecksumCalculation() throws IOException {
        String content1 = "Hello";
        String content2 = "Hello";
        String content3 = "hello";

        String checksum1 = fileManager.calculateChecksum(content1);
        String checksum2 = fileManager.calculateChecksum(content2);
        String checksum3 = fileManager.calculateChecksum(content3);

        assertThat(checksum1).isEqualTo(checksum2);
        assertThat(checksum1).isNotEqualTo(checksum3);
    }

    @Test
    void testSaveFileAfterLoadingWithoutModification() throws IOException, FileManager.OptimisticLockException {
        // Regression test: files always showed as modified externally
        // This test verifies that after loading a file and saving it without changes,
        // no OptimisticLockException is thrown due to timestamp mismatch.
        Path testFile = tempDir.resolve("regression_test.txt");
        String content = "Original content";
        Files.writeString(testFile, content);

        // Load the file (this should capture the correct lastModifiedTime)
        FileManager.FileContent fileContent = fileManager.loadFile(testFile);

        // Wait a bit to ensure time has passed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Save the file with the lastModifiedTime from the loaded content
        // This should NOT throw OptimisticLockException
        fileManager.saveFile(testFile, content, fileContent.getLastModifiedTime());

        // Verify the file still contains the original content
        assertThat(Files.readString(testFile)).isEqualTo(content);
    }

    @Test
    void testMultipleSaveOperationsAfterLoad() throws IOException, FileManager.OptimisticLockException, InterruptedException {
        // Test that multiple save operations work correctly after initial load
        Path testFile = tempDir.resolve("multi_save_test.txt");
        String content1 = "First version";
        Files.writeString(testFile, content1);

        // Load the file
        FileManager.FileContent fileContent1 = fileManager.loadFile(testFile);

        // First modification and save
        String content2 = "Second version";
        fileManager.saveFile(testFile, content2, fileContent1.getLastModifiedTime());
        long lastModifiedTime2 = Files.getLastModifiedTime(testFile).toMillis();

        Thread.sleep(1100); // Ensure timestamp changes

        // Second modification and save
        String content3 = "Third version";
        fileManager.saveFile(testFile, content3, lastModifiedTime2);

        // Verify final content
        assertThat(Files.readString(testFile)).isEqualTo(content3);
    }

    // Helper method to access private calculateChecksum for testing
    private String calculateChecksum(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}