package utils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for common operations.
 */
public class FileUtils {
    
    /**
     * Validates that a path exists and is a directory.
     * 
     * @param path the path to validate
     * @return true if the path exists and is a directory, false otherwise
     */
    public static boolean isValidDirectory(Path path) {
        if (path == null) {
            return false;
        }
        
        if (!Files.exists(path)) {
            return false;
        }
        
        return Files.isDirectory(path);
    }
    
    /**
     * Gets a validation error message for an invalid path.
     * 
     * @param path the path that failed validation
     * @return error message describing why validation failed, or null if path is valid
     */
    public static String getValidationError(Path path) {
        if (path == null) {
            return "Path is null";
        }
        
        if (!Files.exists(path)) {
            return "The specified directory does not exist:\n\n" + path;
        }
        
        if (!Files.isDirectory(path)) {
            return "The specified path is not a directory:\n\n" + path;
        }
        
        return null;
    }
}
