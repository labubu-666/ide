package lsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import language.Languages;
import org.eclipse.lsp4j.Diagnostic;

public class LspServerRegistry {

    private final Map<String, CompletableFuture<LspServerConnection>> futures = new ConcurrentHashMap<>();
    private final Path workspaceRoot;
    private final BiConsumer<String, List<Diagnostic>> onDiagnostics;

    public LspServerRegistry(Path workspaceRoot, BiConsumer<String, List<Diagnostic>> onDiagnostics) {
        this.workspaceRoot = workspaceRoot;
        this.onDiagnostics = onDiagnostics;
    }

    private List<String> commandFor(String extension) {
        var lang = Languages.forExtension(extension);
        return lang.supportsLsp() ? lang.buildLspCommand() : null;
    }

    public CompletableFuture<LspServerConnection> getOrStart(String extension) {
        List<String> command = commandFor(extension);
        if (command == null) return CompletableFuture.completedFuture(null);

        return futures.computeIfAbsent(extension, ext -> {
            try {
                return LspServerConnection.start(command, workspaceRoot, onDiagnostics);
            } catch (IOException e) {
                System.err.println("[LSP] Failed to start server for " + ext + ": " + e.getMessage());
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    public boolean supports(String extension) {
        return commandFor(extension) != null;
    }

    public CompletableFuture<Void> shutdownAll() {
        System.err.println("[LSP Registry] Beginning graceful shutdown of all LSP servers...");
        List<CompletableFuture<Void>> shutdownFutures = futures.values().stream()
            .map(f -> f.thenAccept(conn -> {
                if (conn != null) {
                    System.err.println("[LSP Registry] Shutting down LSP server...");
                    conn.shutdown();
                }
            }))
            .toList();
        
        CompletableFuture<Void> allShutdown = CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0])
        );
        
        futures.clear();
        return allShutdown.thenRun(() -> 
            System.err.println("[LSP Registry] All LSP servers shut down")
        );
    }
}
