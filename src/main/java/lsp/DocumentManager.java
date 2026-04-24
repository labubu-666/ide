package lsp;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

public class DocumentManager {

    private final LspServerRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> versions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> openedDocuments = new ConcurrentHashMap<>();

    public DocumentManager(LspServerRegistry registry) {
        this.registry = registry;
    }

    public void didOpen(Path filePath, String text, String languageId) {
        String ext = ext(filePath);
        if (!registry.supports(ext)) {
            System.err.println("[LSP] No server for extension: " + ext);
            return;
        }
        String uri = uri(filePath);
        
        // Mark document as opened
        openedDocuments.put(uri, true);
        versions.put(uri, new AtomicInteger(1));
        System.err.println("[LSP] didOpen: " + uri);
        
        registry.getOrStart(ext).thenAccept(conn -> {
            if (conn == null) {
                System.err.println("[LSP] No connection available for: " + uri);
                openedDocuments.remove(uri);
                return;
            }
            TextDocumentItem item = new TextDocumentItem(uri, languageId, 1, text);
            conn.server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
            System.err.println("[LSP] Sent didOpen: " + uri);
        });
    }

    /**
     * Open a virtual document (for unsaved files).
     */
    public void didOpenVirtual(String virtualUri, String text, String ext, String languageId) {
        if (!registry.supports(ext)) {
            System.err.println("[LSP] No server for extension: " + ext);
            return;
        }
        
        // Mark document as opened
        openedDocuments.put(virtualUri, true);
        versions.put(virtualUri, new AtomicInteger(1));
        System.err.println("[LSP] didOpen virtual: " + virtualUri);
        
        registry.getOrStart(ext).thenAccept(conn -> {
            if (conn == null) {
                System.err.println("[LSP] No connection available for: " + virtualUri);
                openedDocuments.remove(virtualUri);
                return;
            }
            TextDocumentItem item = new TextDocumentItem(virtualUri, languageId, 1, text);
            conn.server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
            System.err.println("[LSP] Sent didOpen for virtual: " + virtualUri);
        });
    }

    public void didChange(Path filePath, String text) {
        if (!registry.supports(ext(filePath))) return;
        String uri = uri(filePath);
        
        // Only send didChange if document was opened
        if (!openedDocuments.containsKey(uri)) {
            System.err.println("[LSP] Skipping didChange for unopened document: " + uri);
            return;
        }
        
        int version = versions.computeIfAbsent(uri, k -> new AtomicInteger(0)).incrementAndGet();
        registry.getOrStart(ext(filePath)).thenAccept(conn -> {
            if (conn == null) return;
            VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier(uri, version);
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(text);
            conn.server.getTextDocumentService()
                .didChange(new DidChangeTextDocumentParams(id, List.of(change)));
        });
    }

    /**
     * Handle changes for virtual documents (unsaved files).
     */
    public void didChangeVirtual(String virtualUri, String text, String ext) {
        if (!registry.supports(ext)) return;
        
        // Only send didChange if document was opened
        if (!openedDocuments.containsKey(virtualUri)) {
            System.err.println("[LSP] Skipping didChange for unopened virtual document: " + virtualUri);
            return;
        }
        
        int version = versions.computeIfAbsent(virtualUri, k -> new AtomicInteger(0)).incrementAndGet();
        registry.getOrStart(ext).thenAccept(conn -> {
            if (conn == null) return;
            VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier(virtualUri, version);
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(text);
            conn.server.getTextDocumentService()
                .didChange(new DidChangeTextDocumentParams(id, List.of(change)));
        });
    }

    public void didSave(Path filePath) {
        if (!registry.supports(ext(filePath))) return;
        registry.getOrStart(ext(filePath)).thenAccept(conn -> {
            if (conn == null) return;
            conn.server.getTextDocumentService()
                .didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri(filePath))));
        });
    }

    public void didClose(Path filePath) {
        if (!registry.supports(ext(filePath))) return;
        String uri = uri(filePath);
        
        // Mark document as closed
        openedDocuments.remove(uri);
        versions.remove(uri);
        
        registry.getOrStart(ext(filePath)).thenAccept(conn -> {
            if (conn == null) return;
            conn.server.getTextDocumentService()
                .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
        });
    }

    /**
     * Close a virtual document.
     */
    public void didCloseVirtual(String virtualUri, String ext) {
        if (!registry.supports(ext)) return;
        
        // Mark document as closed
        openedDocuments.remove(virtualUri);
        versions.remove(virtualUri);
        
        registry.getOrStart(ext).thenAccept(conn -> {
            if (conn == null) return;
            conn.server.getTextDocumentService()
                .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(virtualUri)));
        });
    }

    private static String uri(Path path) {
        return path.toUri().toString();
    }

    private static String ext(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }
}
