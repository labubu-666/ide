package managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitStatusWatcherTest {

    @Test
    void testWatcherCreation(@TempDir Path tempDir) throws IOException {
        // Create a fake .git directory
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        
        GitStatusWatcher watcher = new GitStatusWatcher(gitDir, tempDir);
        assertThat(watcher).isNotNull();
        
        watcher.stop();
    }

    @Test
    void testWatcherStartAndStop(@TempDir Path tempDir) throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        
        GitStatusWatcher watcher = new GitStatusWatcher(gitDir, tempDir);
        watcher.start();
        
        // Should be able to stop without error
        watcher.stop();
    }
    
    @Test
    void testWatcherWithRefsDirectory(@TempDir Path tempDir) throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        
        // Create refs/heads directory
        Path refsHeads = gitDir.resolve("refs/heads");
        Files.createDirectories(refsHeads);
        
        GitStatusWatcher watcher = new GitStatusWatcher(gitDir, tempDir);
        watcher.start();
        
        // Should start without error even with refs directory
        watcher.stop();
    }
    
    @Test
    void testAddAndRemoveListener(@TempDir Path tempDir) throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        
        GitStatusWatcher watcher = new GitStatusWatcher(gitDir, tempDir);
        
        java.util.function.Consumer<GitStatusWatcher.GitChangeType> listener = 
            changeType -> System.out.println("Change detected: " + changeType);
        
        // Should be able to add and remove listeners without error
        watcher.addListener(listener);
        watcher.removeListener(listener);
        
        watcher.stop();
    }
    
    @Test
    void testMultipleStartCallsIdempotent(@TempDir Path tempDir) throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        
        GitStatusWatcher watcher = new GitStatusWatcher(gitDir, tempDir);
        
        // Starting multiple times should be safe
        watcher.start();
        watcher.start();
        watcher.start();
        
        watcher.stop();
    }
}
