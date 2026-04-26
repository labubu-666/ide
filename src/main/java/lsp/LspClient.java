package lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LspClient implements LanguageClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LspClient.class);
    private final BiConsumer<String, List<Diagnostic>> onDiagnostics;

    LspClient(BiConsumer<String, List<Diagnostic>> onDiagnostics) {
        this.onDiagnostics = onDiagnostics;
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        LOGGER.info("[LSP] diagnostics for {} → {} item(s)", params.getUri(),
            params.getDiagnostics().size());
        onDiagnostics.accept(params.getUri(), params.getDiagnostics());
    }

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        // Handle progress notifications from the language server
        // For now, we can ignore these or log them if needed
    }
}
