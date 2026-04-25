package managers;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Watches the .git directory for changes and notifies listeners.
 * Monitors:
 * - .git/HEAD (branch switches)
 * - .git/index (staged changes)
 * - .git/refs/heads/* (new commits)
 * - Working directory (unstaged changes)
 */
public class GitStatusWatcher {
    
    private final Path gitDir;
    private final Path workingDir;
    private final List<Consumer<GitChangeType>> listeners = new ArrayList<>();
    private WatchService watchService;
    private ExecutorService executor;
    private volatile boolean running = false;
    
    /**
     * Types of git changes that can be detected.
     */
    public enum GitChangeType {
        BRANCH_CHANGED,  // HEAD file changed
        INDEX_CHANGED,   // Staged changes
        REFS_CHANGED,    // Commits/branch references
        WORKING_DIR_CHANGED  // Working directory files
    }
    
    /**
     * Creates a new GitStatusWatcher for the given repository.
     * 
     * @param gitDir the .git directory path
     * @param workingDir the working directory path
     * @throws IOException if the watch service cannot be created
     */
    public GitStatusWatcher(Path gitDir, Path workingDir) throws IOException {
        this.gitDir = gitDir;
        this.workingDir = workingDir;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GitStatusWatcher");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Starts watching for git changes.
     */
    public void start() {
        if (running) {
            return;
        }
        
        try {
            // Watch .git directory for HEAD and index changes
            gitDir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);
            
            // Watch .git/refs/heads for commit changes
            Path refsHeads = gitDir.resolve("refs/heads");
            if (Files.exists(refsHeads)) {
                refsHeads.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            }
            
            // Watch working directory for file changes
            registerWorkingDirRecursive(workingDir);
            
            running = true;
            executor.submit(this::watchLoop);
            
        } catch (IOException e) {
            System.err.println("[GitStatusWatcher] Failed to start watching: " + e.getMessage());
        }
    }
    
    /**
     * Registers the working directory and its subdirectories for watching.
     * Excludes the .git directory itself.
     */
    private void registerWorkingDirRecursive(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        
        Files.walk(dir)
            .filter(Files::isDirectory)
            .filter(p -> !p.startsWith(gitDir)) // Exclude .git directory
            .filter(p -> !p.getFileName().toString().startsWith(".")) // Exclude other hidden dirs
            .forEach(p -> {
                try {
                    p.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException e) {
                    // Silently skip directories we can't watch
                }
            });
    }
    
    /**
     * Main watch loop that processes file system events.
     */
    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                
                // Determine what changed and notify listeners
                Path parent = (Path) key.watchable();
                GitChangeType changeType = determineChangeType(parent, filename);
                
                if (changeType != null) {
                    notifyListeners(changeType);
                }
            }
            
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }
    
    /**
     * Determines the type of git change based on the file path.
     */
    private GitChangeType determineChangeType(Path parent, Path filename) {
        String name = filename.toString();
        
        // Check if it's in the .git directory
        if (parent.equals(gitDir)) {
            if ("HEAD".equals(name)) {
                return GitChangeType.BRANCH_CHANGED;
            } else if ("index".equals(name)) {
                return GitChangeType.INDEX_CHANGED;
            }
        }
        
        // Check if it's in .git/refs/heads
        if (parent.startsWith(gitDir.resolve("refs/heads"))) {
            return GitChangeType.REFS_CHANGED;
        }
        
        // Check if it's in the working directory
        if (parent.startsWith(workingDir) && !parent.startsWith(gitDir)) {
            return GitChangeType.WORKING_DIR_CHANGED;
        }
        
        return null;
    }
    
    /**
     * Notifies all listeners of a git change on the JavaFX application thread.
     */
    private void notifyListeners(GitChangeType changeType) {
        Platform.runLater(() -> {
            for (Consumer<GitChangeType> listener : listeners) {
                try {
                    listener.accept(changeType);
                } catch (Exception e) {
                    System.err.println("[GitStatusWatcher] Error in listener: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Adds a listener to be notified of git changes.
     * 
     * @param listener the listener to add
     */
    public void addListener(Consumer<GitChangeType> listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a listener.
     * 
     * @param listener the listener to remove
     */
    public void removeListener(Consumer<GitChangeType> listener) {
        listeners.remove(listener);
    }
    
    /**
     * Stops watching and releases resources.
     */
    public void stop() {
        running = false;
        
        if (executor != null) {
            executor.shutdown();
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                System.err.println("[GitStatusWatcher] Error closing watch service: " + e.getMessage());
            }
        }
    }
}
