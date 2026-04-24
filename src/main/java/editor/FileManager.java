package editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Handles file operations with checksum-based modification detection and optimistic locking.
 * This class provides testable business logic for file CRUD operations.
 */
public class FileManager {

    /**
     * Represents the result of loading a file.
     */
    public static class FileContent {
        private final String content;
        private final String checksum;
        private final long lastModifiedTime;

        public FileContent(String content, String checksum, long lastModifiedTime) {
            this.content = content;
            this.checksum = checksum;
            this.lastModifiedTime = lastModifiedTime;
        }

        public String getContent() { return content; }
        public String getChecksum() { return checksum; }
        public long getLastModifiedTime() { return lastModifiedTime; }
    }

    /**
     * Exception thrown when optimistic locking fails.
     */
    public static class OptimisticLockException extends Exception {
        public OptimisticLockException(String message) {
            super(message);
        }
    }

    /**
     * Loads a file and calculates its initial checksum for modification tracking.
     */
    public FileContent loadFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }

        String content = Files.readString(path);
        String checksum = calculateChecksum(content);
        long lastModifiedTime = Files.getLastModifiedTime(path).toMillis();

        return new FileContent(content, checksum, lastModifiedTime);
    }

    /**
     * Saves content to a file with optimistic locking check.
     * @throws OptimisticLockException if the file was modified externally since it was loaded
     */
    public void saveFile(Path path, String content, long expectedLastModifiedTime) throws IOException, OptimisticLockException {
        if (Files.exists(path)) {
            long currentLastModifiedTime = Files.getLastModifiedTime(path).toMillis();
            if (currentLastModifiedTime != expectedLastModifiedTime) {
                throw new OptimisticLockException("File was modified externally: " + path);
            }
        }

        Files.writeString(path, content);
        // Update the last modified time to the current time
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
    }

    /**
     * Determines if the current content differs from the original file content.
     */
    public boolean isModified(String currentContent, String originalChecksum) {
        if (originalChecksum == null) {
            // For new/unsaved files, always consider as modified if there's content
            return !currentContent.isEmpty();
        }
        String currentChecksum = calculateChecksum(currentContent);
        return !currentChecksum.equals(originalChecksum);
    }

    /**
     * Calculates SHA-256 checksum of the given content.
     * Public for use by UI components and testing.
     */
    public String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}