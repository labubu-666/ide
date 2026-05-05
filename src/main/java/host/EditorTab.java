package host;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.SplitPane;
import javafx.stage.Popup;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import managers.FileManager;
import language.Languages;

class EditorTab extends Tab {
    final CodeArea codeArea;
    final VirtualizedScrollPane<CodeArea> editorPane;
    final String extension;
    Path filePath;
    String baseTitle;
    boolean modified = false;
    ViewMode currentViewMode = ViewMode.EDITOR;
    Node cachedPreviewNode = null;
    Subscription cleanup;
    Subscription dirtyCleanup;
    Subscription lspCleanup;
    Subscription previewCleanup;
    List<Diagnostic> currentDiagnostics = new ArrayList<>();
    CompletionPopup completionPopup;
    String virtualUri;
    List<CompletionItem> allCompletionItems;
    
    private String originalChecksum;
    long fileLastModifiedTime;

    private final EditorContext ctx;
    private final FileManager fileManager = new FileManager();

    EditorTab(String title, String initialText, String ext, Path path, EditorContext ctx) {
        super(title);
        this.ctx = ctx;
        this.extension = ext;
        this.filePath = path;
        this.baseTitle = title;
        this.setClosable(true);

        if (path == null) {
            this.virtualUri = "untitled:" + System.currentTimeMillis() + "." + ext;
            System.err.println("[EditorTab] Created virtual URI: " + virtualUri);
            ctx.docManager().didOpenVirtual(virtualUri, initialText, ext, Languages.forExtension(ext).languageId());
        } else {
            this.virtualUri = null;
        }

        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        editorPane = new VirtualizedScrollPane<>(codeArea);
        setContent(editorPane);

        cleanup = codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .retainLatestUntilLater(ctx.executor())
                .supplyTask(() -> {
                    String text = codeArea.getText();
                    Task<StyleSpans<Collection<String>>> task = new Task<>() {
                        @Override
                        protected StyleSpans<Collection<String>> call() throws Exception {
                            var styler = Languages.forExtension(extension).styler();
                            return styler != null ? styler.apply(text) : null;
                        }
                    };
                    ctx.executor().execute(task);
                    return task;
                })
                .awaitLatest(codeArea.multiPlainChanges())
                .filterMap(t -> {
                    if (t.isSuccess() && t.get() != null) {
                        return Optional.of(t.get());
                    } else if (t.isFailure()) {
                        t.getFailure().printStackTrace();
                    }
                    return Optional.empty();
                })
                .subscribe(h -> {
                    String t = codeArea.getText();
                    if (h.length() == t.length())
                        codeArea.setStyleSpans(0, mergeWithDiagnostics(h, t));
                    else
                        codeArea.setStyleSpans(0, h);
                });

        codeArea.replaceText(initialText);
        if (!initialText.isEmpty()) {
            StyleSpans<Collection<String>> spans = null;
            var styler = Languages.forExtension(ext).styler();
            if (styler != null) spans = styler.apply(initialText);
            if (spans != null) codeArea.setStyleSpans(0, spans);
        }

        Platform.runLater(() -> {
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
        });

        // Initialize checksum for modification tracking
        this.originalChecksum = fileManager.calculateChecksum(initialText);
        this.fileLastModifiedTime = 0;

        // Use checksum-based modification detection instead of simple flag
        dirtyCleanup = codeArea.multiPlainChanges().subscribe(ch -> updateModificationStatus());

        lspCleanup = codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(500))
            .subscribe(changes -> {
                if (filePath != null) {
                    ctx.docManager().didChange(filePath, codeArea.getText());
                } else if (virtualUri != null) {
                    ctx.docManager().didChangeVirtual(virtualUri, codeArea.getText(), extension);
                }
            });

        // Live preview: refresh preview when buffer changes (debounced)
        previewCleanup = codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(500))
            .subscribe(changes -> {
                // Only update if preview is available for this extension and
                // the current view mode actually shows the preview
                if (ctx.previewRegistry().hasPreview(extension)
                        && (currentViewMode == ViewMode.SPLIT || currentViewMode == ViewMode.PREVIEW)) {
                    Platform.runLater(this::refreshPreview);
                }
            });

        TextArea diagArea = new TextArea();
        diagArea.setEditable(false);
        diagArea.setWrapText(true);
        diagArea.setPrefWidth(380);
        diagArea.setPrefRowCount(3);
        Popup diagPopup = new Popup();
        diagPopup.setAutoHide(true);
        diagPopup.getContent().add(diagArea);
        codeArea.setMouseOverTextDelay(Duration.ofMillis(400));
        codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            findDiagnosticAt(e.getCharacterIndex()).ifPresent(d -> {
                String severity = d.getSeverity() != null ? d.getSeverity().toString() : "INFO";
                diagArea.setText("[" + severity + "] " + d.getMessage());
                diagPopup.show(codeArea, e.getScreenPosition().getX(),
                    e.getScreenPosition().getY() + 20);
            });
        });

        completionPopup = new CompletionPopup(codeArea);
        completionPopup.setOnSelect(item -> insertCompletion(item));

        // Store all received completion items so we can filter them as the user types
        this.allCompletionItems = new ArrayList<>();

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                requestCompletion();
                event.consume();
                return;
            }

            if (completionPopup.isShowing()) {
                KeyCode code = event.getCode();
                if (code == KeyCode.ENTER || code == KeyCode.ESCAPE || code == KeyCode.TAB) {
                    // Let the popup's internal handler deal with these.
                    // We must consume the event so the CodeArea does not
                    // insert a new line, lose focus, etc.
                    event.consume();
                } else if (code == KeyCode.DOWN || code == KeyCode.UP ||
                           code == KeyCode.PAGE_DOWN || code == KeyCode.PAGE_UP) {
                    // These are already handled by ListView's built-in
                    // navigation. We just need to prevent the CodeArea
                    // from consuming them (e.g. move caret).
                    event.consume();
                }
            }
        });

        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(200))
            .subscribe(changes -> {
                if (completionPopup.isShowing()) {
                    String wordAtCursor = getWordAtCursor();
                    completionPopup.filter(wordAtCursor, new ArrayList<>());
                }
            });
    }

    private void requestCompletion() {
        System.err.println("[Completion] Ctrl+Space pressed");

        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();

        String[] lines = text.substring(0, caretPos).split("\n", -1);
        int line = lines.length - 1;
        int character = lines[lines.length - 1].length();

        String uri = documentUri();
        if (uri == null) {
            System.err.println("[Completion] No URI available");
            return;
        }

        System.err.println("[Completion] Requesting at " + uri + " line=" + line + " char=" + character);

        ctx.completionProvider().requestCompletion(extension, uri, line, character)
            .thenAccept(items -> {
                System.err.println("[Completion] Received " + items.size() + " items");
                if (!items.isEmpty()) {
                    Platform.runLater(() -> completionPopup.show(items));
                } else {
                    completionPopup.hide();
                }
            });
    }

    private String getWordAtCursor() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();

        int start = caretPos;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        return text.substring(start, caretPos).toLowerCase();
    }

    private void insertCompletion(CompletionItem item) {
        String insertText = item.getInsertText();
        if (insertText == null) {
            insertText = item.getLabel();
        }
        if (insertText == null) {
            System.err.println("[InsertCompletion] No insert text available");
            return;
        }

        System.err.println("[InsertCompletion] Inserting: " + insertText);

        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int start = caretPos;

        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (Character.isJavaIdentifierPart(ch)) {
                start--;
            } else {
                break;
            }
        }

        System.err.println("[InsertCompletion] Replacing from " + start + " to " + caretPos);

        codeArea.replaceText(start, caretPos, insertText);
        codeArea.moveTo(start + insertText.length());
    }

    String documentUri() {
        if (filePath != null) return filePath.toUri().toString();
        return virtualUri;
    }

    void setModified(boolean value) {
        this.modified = value;
        setText(value ? "*" + baseTitle : baseTitle);
    }

    /**
     * Updates the modification status based on checksum comparison.
     * The modified flag is set to true only if the current content differs from the original.
     */
    private void updateModificationStatus() {
        boolean isModified = fileManager.isModified(codeArea.getText(), originalChecksum);
        setModified(isModified);
    }

    /**
     * Called after a file is saved to reset the checksum tracker.
     */
    void markAsSaved(long newLastModifiedTime) {
        this.originalChecksum = fileManager.calculateChecksum(codeArea.getText());
        this.fileLastModifiedTime = newLastModifiedTime;
        setModified(false);
    }

    /**
     * Called when a file is loaded to initialize the checksum tracker.
     */
    void initializeWithFileContent(FileManager.FileContent fileContent) {
        this.originalChecksum = fileContent.getChecksum();
        this.fileLastModifiedTime = fileContent.getLastModifiedTime();
        setModified(false);
    }

    void dispose() {
        if (cleanup != null) cleanup.unsubscribe();
        if (dirtyCleanup != null) dirtyCleanup.unsubscribe();
        if (lspCleanup != null) lspCleanup.unsubscribe();
        if (previewCleanup != null) previewCleanup.unsubscribe();

        if (virtualUri != null) {
            ctx.docManager().didCloseVirtual(virtualUri, extension);
        }
    }

    void applyViewMode(ViewMode mode) {
        this.currentViewMode = mode;
        switch (mode) {
            case EDITOR -> setContent(editorPane);
            case SPLIT -> {
                if (cachedPreviewNode == null) buildPreviewNode();
                if (cachedPreviewNode != null) {
                    SplitPane sp = new SplitPane(editorPane, cachedPreviewNode);
                    sp.setDividerPositions(0.5);
                    setContent(sp);
                } else {
                    setContent(editorPane);
                }
            }
            case PREVIEW -> {
                if (cachedPreviewNode == null) buildPreviewNode();
                setContent(cachedPreviewNode != null ? cachedPreviewNode : editorPane);
            }
        }
        ctx.onViewModeChanged().accept(this);
    }

    void refreshPreview() {
        if (!ctx.previewRegistry().hasPreview(extension)) return;
        buildPreviewNode();
        if (currentViewMode != ViewMode.EDITOR) applyViewMode(currentViewMode);
    }

    private void buildPreviewNode() {
        ctx.previewRegistry().forExtension(extension)
            .ifPresent(r -> cachedPreviewNode = r.render(codeArea.getText(), filePath, ctx.onOpenFile()));
    }

    void setDiagnostics(List<Diagnostic> diagnostics) {
        this.currentDiagnostics = diagnostics;
        String text = codeArea.getText();
        if (text.isEmpty()) return;
        StyleSpans<Collection<String>> syntax = computeSyntaxSpans(text);
        if (syntax != null) codeArea.setStyleSpans(0, mergeWithDiagnostics(syntax, text));
    }

    private StyleSpans<Collection<String>> computeSyntaxSpans(String text) {
        var styler = Languages.forExtension(extension).styler();
        return styler != null ? styler.apply(text) : null;
    }

    private StyleSpans<Collection<String>> mergeWithDiagnostics(
            StyleSpans<Collection<String>> syntax, String text) {
        if (currentDiagnostics.isEmpty()) return syntax;
        StyleSpans<Collection<String>> diagSpans = computeDiagnosticSpans(text);
        return syntax.overlay(diagSpans, (a, b) -> {
            if (b.isEmpty()) return a;
            Set<String> combined = new LinkedHashSet<>(a);
            combined.addAll(b);
            return combined;
        });
    }

    private StyleSpans<Collection<String>> computeDiagnosticSpans(String text) {
        int length = text.length();
        StyleSpansBuilder<Collection<String>> sb = new StyleSpansBuilder<>();
        if (length == 0 || currentDiagnostics.isEmpty()) {
            sb.add(Collections.emptyList(), Math.max(0, length));
            return sb.create();
        }
        String[] lines = text.split("\n", -1);
        List<int[]> intervals = new ArrayList<>();
        for (Diagnostic d : currentDiagnostics) {
            try {
                int start = lineColToOffset(lines,
                    d.getRange().getStart().getLine(), d.getRange().getStart().getCharacter());
                int end = lineColToOffset(lines,
                    d.getRange().getEnd().getLine(), d.getRange().getEnd().getCharacter());
                if (end > start && start < length) {
                    int type = (d.getSeverity() == DiagnosticSeverity.Error) ? 1 : 0;
                    intervals.add(new int[]{Math.min(start, length), Math.min(end, length), type});
                }
            } catch (Exception ignored) {}
        }
        intervals.sort((a, b) -> Integer.compare(a[0], b[0]));
        int pos = 0;
        for (int[] iv : intervals) {
            if (iv[0] > pos) sb.add(Collections.emptyList(), iv[0] - pos);
            if (iv[0] >= pos) {
                sb.add(Collections.singletonList(iv[2] == 1 ? "lsp-error" : "lsp-warning"),
                    iv[1] - iv[0]);
                pos = iv[1];
            }
        }
        if (pos < length) sb.add(Collections.emptyList(), length - pos);
        return sb.create();
    }

    private Optional<Diagnostic> findDiagnosticAt(int offset) {
        String text = codeArea.getText();
        if (text.isEmpty() || currentDiagnostics.isEmpty()) return Optional.empty();
        String[] lines = text.split("\n", -1);
        return currentDiagnostics.stream().filter(d -> {
            int start = lineColToOffset(lines,
                d.getRange().getStart().getLine(), d.getRange().getStart().getCharacter());
            int end = lineColToOffset(lines,
                d.getRange().getEnd().getLine(), d.getRange().getEnd().getCharacter());
            return offset >= start && offset <= end;
        }).findFirst();
    }

    private static int lineColToOffset(String[] lines, int line, int col) {
        int l = Math.min(line, lines.length - 1);
        int offset = 0;
        for (int i = 0; i < l; i++) offset += lines[i].length() + 1;
        return offset + Math.min(col, l < lines.length ? lines[l].length() : 0);
    }
}
