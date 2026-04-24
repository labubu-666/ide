package lsp;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class CompletionProvider {

    private final LspServerRegistry registry;

    public CompletionProvider(LspServerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Request completions from the LSP server at the given position.
     *
     * @param ext the file extension
     * @param uri the document URI
     * @param line the line number (0-based)
     * @param character the character position (0-based)
     * @return a CompletableFuture with the list of completion items, or an empty list if not available
     */
    public CompletableFuture<List<CompletionItem>> requestCompletion(
            String ext, String uri, int line, int character) {
        
        if (!registry.supports(ext)) {
            System.err.println("[CompletionProvider] Extension not supported: " + ext);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        System.err.println("[CompletionProvider] Requesting completion for ext=" + ext + " uri=" + uri + " pos=" + line + ":" + character);

        return registry.getOrStart(ext).thenCompose(conn -> {
            if (conn == null) {
                System.err.println("[CompletionProvider] No connection available for completion");
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            System.err.println("[CompletionProvider] Connection available, sending request...");

            CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character)
            );

            return conn.server.getTextDocumentService().completion(params)
                .thenApply(response -> {
                    System.err.println("[CompletionProvider] Response received: " + (response != null ? response.getClass().getName() : "null"));
                    List<CompletionItem> items = extractCompletionItems(response);
                    System.err.println("[CompletionProvider] Extracted " + items.size() + " items");
                    return items;
                })
                .exceptionally(ex -> {
                    System.err.println("[CompletionProvider] Completion request failed: " + ex.getMessage());
                    ex.printStackTrace();
                    return Collections.emptyList();
                });
        });
    }

    /**
     * Extract completion items from Either<List<CompletionItem>, CompletionList>.
     */
    private List<CompletionItem> extractCompletionItems(Object response) {
        if (response == null) {
            System.err.println("[CompletionProvider] Response is null");
            return Collections.emptyList();
        }
        
        try {
            // Use reflection to check if it's left or right
            var either = response;
            Boolean isLeft = (Boolean) either.getClass().getMethod("isLeft").invoke(either);
            Boolean isRight = (Boolean) either.getClass().getMethod("isRight").invoke(either);
            
            System.err.println("[CompletionProvider] Either isLeft=" + isLeft + " isRight=" + isRight);
            
            if (isLeft != null && isLeft) {
                // Left side - List<CompletionItem>
                Object left = either.getClass().getMethod("getLeft").invoke(either);
                System.err.println("[CompletionProvider] Left value: " + (left != null ? left.getClass().getName() : "null"));
                
                if (left instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<CompletionItem> items = (List<CompletionItem>) left;
                    System.err.println("[CompletionProvider] Extracted " + items.size() + " items from List");
                    return items != null ? items : Collections.emptyList();
                } else if (left instanceof CompletionList) {
                    CompletionList list = (CompletionList) left;
                    List<CompletionItem> items = list.getItems();
                    System.err.println("[CompletionProvider] Extracted " + (items != null ? items.size() : 0) + " items from CompletionList");
                    return items != null ? items : Collections.emptyList();
                }
            } else if (isRight != null && isRight) {
                // Right side - CompletionList
                Object right = either.getClass().getMethod("getRight").invoke(either);
                System.err.println("[CompletionProvider] Right value: " + (right != null ? right.getClass().getName() : "null"));
                
                if (right instanceof CompletionList) {
                    CompletionList list = (CompletionList) right;
                    List<CompletionItem> items = list.getItems();
                    System.err.println("[CompletionProvider] Extracted " + (items != null ? items.size() : 0) + " items from CompletionList");
                    return items != null ? items : Collections.emptyList();
                } else if (right instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<CompletionItem> items = (List<CompletionItem>) right;
                    System.err.println("[CompletionProvider] Extracted " + items.size() + " items from List");
                    return items != null ? items : Collections.emptyList();
                }
            }
        } catch (Exception e) {
            System.err.println("[CompletionProvider] Failed to extract completion items: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.err.println("[CompletionProvider] Could not extract items from response");
        return Collections.emptyList();
    }

    /**
     * Resolve a completion item to get more details (documentation, additional edits, etc.).
     *
     * @param ext the file extension
     * @param item the completion item to resolve
     * @return a CompletableFuture with the resolved completion item
     */
    public CompletableFuture<CompletionItem> resolveCompletion(String ext, CompletionItem item) {
        
        if (!registry.supports(ext)) {
            return CompletableFuture.completedFuture(item);
        }

        return registry.getOrStart(ext).thenCompose(conn -> {
            if (conn == null) {
                return CompletableFuture.completedFuture(item);
            }

            return conn.server.getTextDocumentService().resolveCompletionItem(item)
                .exceptionally(ex -> {
                    System.err.println("[LSP] Completion resolve failed: " + ex.getMessage());
                    return item;
                });
        });
    }
}
