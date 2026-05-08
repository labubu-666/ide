package lsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

public class LspServerConnection {

    final LanguageServer server;
    private final Process process;

    private LspServerConnection(LanguageServer server, Process process) {
        this.server = server;
        this.process = process;
    }

    public static CompletableFuture<LspServerConnection> start(
            List<String> command,
            Path workspaceRoot,
            BiConsumer<String, List<Diagnostic>> onDiagnostics) throws IOException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspaceRoot.toFile());
        // Redirect stderr to stdout for debugging
        pb.redirectErrorStream(true);
        System.err.println("[LSP] ProcessBuilder command: " + command);
        System.err.println("[LSP] Working directory: " + workspaceRoot.toAbsolutePath());
        Process process = pb.start();

        LspClient client = new LspClient(onDiagnostics);
        var launcher = LSPLauncher.createClientLauncher(
                client, process.getInputStream(), process.getOutputStream());
        LanguageServer server = launcher.getRemoteProxy();
        launcher.startListening();

        InitializeParams params = new InitializeParams();
        params.setProcessId((int) ProcessHandle.current().pid());
        params.setRootUri(workspaceRoot.toUri().toString());

        TextDocumentClientCapabilities textDoc = new TextDocumentClientCapabilities();
        SynchronizationCapabilities sync = new SynchronizationCapabilities();
        sync.setDidSave(true);
        textDoc.setSynchronization(sync);
        textDoc.setPublishDiagnostics(new PublishDiagnosticsCapabilities());
        
        // Enable completion support
        CompletionItemCapabilities completionItem = new CompletionItemCapabilities();
        completionItem.setSnippetSupport(true);
        completionItem.setDocumentationFormat(List.of("plaintext", "markdown"));
        CompletionCapabilities completion = new CompletionCapabilities();
        completion.setCompletionItem(completionItem);
        textDoc.setCompletion(completion);
        
        ClientCapabilities caps = new ClientCapabilities();
        caps.setTextDocument(textDoc);
        params.setCapabilities(caps);

        LspServerConnection conn = new LspServerConnection(server, process);

        System.err.println("[LSP] Starting: " + command);
        return server.initialize(params).thenApply(result -> {
            server.initialized(new InitializedParams());
            System.err.println("[LSP] Ready: " + command);
            return conn;
        }).exceptionally(ex -> {
            System.err.println("[LSP] Initialize failed: " + ex.getMessage());
            process.destroyForcibly();
            return null;
        });
    }

    public void shutdown() {
        server.shutdown().orTimeout(3, TimeUnit.SECONDS).thenRun(() -> {
            server.exit();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
            }
        }).exceptionally(ex -> {
            process.destroyForcibly();
            return null;
        });
    }
}
