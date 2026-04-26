package editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import language.Languages;

/**
 * A tab that shows a side-by-side diff for a given file. Right side is editable.
 */
public class DiffEditorTab extends javafx.scene.control.Tab {
    final Path filePath;
    private final EditorContext ctx;
    private final DiffComputer diffComputer;
    private final managers.GitManager gitManager;

    private final CodeArea leftArea;
    private final CodeArea rightArea;

    public DiffEditorTab(Path filePath, EditorContext ctx, managers.GitManager gitManager) {
        super("Diff - " + filePath.getFileName().toString());
        this.filePath = filePath;
        this.ctx = ctx;
        if (gitManager != null && gitManager.isGitRepository()) {
            this.diffComputer = new DiffComputer(gitManager.getRepository(), gitManager.getRootPath());
        } else {
            this.diffComputer = null;
        }
        this.gitManager = gitManager;

        setClosable(true);

        leftArea = new CodeArea();
        leftArea.setEditable(false);
        rightArea = new CodeArea();

        VirtualizedScrollPane<CodeArea> leftPane = new VirtualizedScrollPane<>(leftArea);
        VirtualizedScrollPane<CodeArea> rightPane = new VirtualizedScrollPane<>(rightArea);

        SplitPane sp = new SplitPane(leftPane, rightPane);
        sp.setDividerPositions(0.5);

        ToolBar actions = buildActionBar();

        BorderPane root = new BorderPane();
        root.setTop(actions);
        root.setCenter(sp);
        BorderPane.setMargin(sp, new Insets(6));

        setContent(root);

        loadContents();
    }

    private ToolBar buildActionBar() {
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> loadContents());
        Button stageAll = new Button("Stage All");
        stageAll.setOnAction(e -> {
            if (gitManager != null) {
                boolean ok = gitManager.stageFile(filePath);
                System.err.println("[Diff] Stage file " + filePath + " => " + ok);
            }
        });
        Button stageFile = stageAll;
        Button revert = new Button("Revert");
        revert.setOnAction(e -> {
            // naive revert: replace right content with left
            rightArea.replaceText(leftArea.getText());
        });
        Button revertSelection = new Button("Revert Selection");
        revertSelection.setOnAction(e -> revertSelection());
        Button jumpToFile = new Button("Jump to File");
        jumpToFile.setOnAction(e -> ctx.onOpenFile().accept(filePath));

        return new ToolBar(refresh, stageAll, revert, revertSelection, jumpToFile);
    }

    private void revertSelection() {
        int selStart = rightArea.getSelection().getStart();
        int selEnd = rightArea.getSelection().getEnd();
        if (selStart >= selEnd) return;
        String rightText = rightArea.getText();
        String leftText = leftArea.getText();

        int startLine = countLines(rightText, selStart);
        int endLine = countLines(rightText, Math.max(0, selEnd - 1));

        int leftStartOffset = offsetOfLine(leftText, startLine);
        int leftEndOffset = offsetOfLine(leftText, endLine + 1);
        if (leftStartOffset < 0) leftStartOffset = 0;
        if (leftEndOffset < 0) leftEndOffset = leftText.length();

        String replacement = leftText.substring(Math.min(leftStartOffset, leftText.length()), Math.min(leftEndOffset, leftText.length()));
        rightArea.replaceText(selStart, selEnd, replacement);
    }

    private int countLines(String text, int offset) {
        if (offset <= 0) return 0;
        int lines = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) if (text.charAt(i) == '\n') lines++;
        return lines;
    }

    private int offsetOfLine(String text, int line) {
        if (line <= 0) return 0;
        int cnt = 0;
        for (int i = 0; i < text.length(); i++) {
            if (cnt == line) return i;
            if (text.charAt(i) == '\n') cnt++;
        }
        return text.length();
    }

    private StyleSpans<Collection<String>> mergeDiffSpans(
            StyleSpans<Collection<String>> syntaxSpans,
            List<DiffComputer.Hunk> hunks,
            String text,
            boolean isLeft) {
        if (text.isEmpty()) return syntaxSpans;
        
        int length = text.length();
        List<int[]> diffIntervals = new ArrayList<>();
        
        // Build intervals for added/deleted lines
        for (DiffComputer.Hunk hunk : hunks) {
            if (isLeft) {
                // Left side: mark old lines as deleted
                int lineOffset = 0;
                String[] lines = text.split("\n", -1);
                for (int i = 0; i < hunk.oldStart() - 1 && i < lines.length; i++) {
                    lineOffset += lines[i].length() + 1;
                }
                for (String oldLine : hunk.oldLinesText()) {
                    int lineStart = lineOffset;
                    int lineEnd = lineOffset + oldLine.length() + 1;
                    diffIntervals.add(new int[]{Math.min(lineStart, length), Math.min(lineEnd, length), 0}); // 0 = deleted
                    lineOffset = lineEnd;
                }
            } else {
                // Right side: mark new lines as added
                int lineOffset = 0;
                String[] lines = text.split("\n", -1);
                for (int i = 0; i < hunk.newStart() - 1 && i < lines.length; i++) {
                    lineOffset += lines[i].length() + 1;
                }
                for (String newLine : hunk.newLinesText()) {
                    int lineStart = lineOffset;
                    int lineEnd = lineOffset + newLine.length() + 1;
                    diffIntervals.add(new int[]{Math.min(lineStart, length), Math.min(lineEnd, length), 1}); // 1 = added
                    lineOffset = lineEnd;
                }
            }
        }
        
        if (diffIntervals.isEmpty()) return syntaxSpans;
        
        // Create diff style spans
        StyleSpansBuilder<Collection<String>> diffSb = new StyleSpansBuilder<>();
        int pos = 0;
        for (int[] iv : diffIntervals) {
            if (iv[0] > pos) diffSb.add(Collections.emptyList(), iv[0] - pos);
            String styleClass = iv[2] == 0 ? "diff-deleted" : "diff-added";
            diffSb.add(Collections.singleton(styleClass), iv[1] - iv[0]);
            pos = iv[1];
        }
        if (pos < length) diffSb.add(Collections.emptyList(), length - pos);
        
        StyleSpans<Collection<String>> diffSpans = diffSb.create();
        
        // Merge with syntax spans if available
        if (syntaxSpans != null) {
            return syntaxSpans.overlay(diffSpans, (a, b) -> {
                if (b.isEmpty()) return a;
                Set<String> combined = new LinkedHashSet<>(a);
                combined.addAll(b);
                return combined;
            });
        }
        
        return diffSpans;
    }

    private void loadContents() {
        Platform.runLater(() -> {
            if (diffComputer == null) {
                leftArea.replaceText("");
                rightArea.replaceText("");
                return;
            }
            String head = diffComputer.loadHeadContent(filePath);
            String work = diffComputer.loadWorkingTreeContent(filePath);
            leftArea.replaceText(head);
            rightArea.replaceText(work);

            // Apply syntax highlighting based on file extension
            String ext = "";
            var fn = filePath.getFileName();
            if (fn != null) {
                String name = fn.toString();
                int d = name.lastIndexOf('.');
                if (d > 0) ext = name.substring(d + 1);
            }
            
            // Apply syntax highlighting using styler
            var language = Languages.forExtension(ext);
            var styler = language.styler();
            
            StyleSpans<Collection<String>> leftSpans = null;
            StyleSpans<Collection<String>> rightSpans = null;
            
            if (styler != null) {
                if (!head.isEmpty()) {
                    leftSpans = styler.apply(head);
                }
                if (!work.isEmpty()) {
                    rightSpans = styler.apply(work);
                }
            }
            
            // Compute diff hunks and apply diff styling
            List<DiffComputer.Hunk> hunks = diffComputer != null ? diffComputer.computeLineHunks(head, work) : new ArrayList<>();
            
            // Merge syntax highlighting with diff highlighting
            if (!hunks.isEmpty()) {
                leftSpans = mergeDiffSpans(leftSpans, hunks, head, true);
                rightSpans = mergeDiffSpans(rightSpans, hunks, work, false);
            }
            
            if (leftSpans != null) leftArea.setStyleSpans(0, leftSpans);
            if (rightSpans != null) rightArea.setStyleSpans(0, rightSpans);
            
            // Apply language-specific stylesheet and diff stylesheet
            String stylesheetResource = language.stylesheetResource();
            var scene = getTabPane().getScene();
            if (scene != null) {
                if (stylesheetResource != null && !scene.getStylesheets().contains(DiffEditorTab.class.getResource(stylesheetResource).toExternalForm())) {
                    String stylesheetUrl = DiffEditorTab.class.getResource(stylesheetResource).toExternalForm();
                    scene.getStylesheets().add(stylesheetUrl);
                }
                String lspStyleUrl = DiffEditorTab.class.getResource("/editor/keywords/lsp.css").toExternalForm();
                if (!scene.getStylesheets().contains(lspStyleUrl)) {
                    scene.getStylesheets().add(lspStyleUrl);
                }
                String diffStyleUrl = DiffEditorTab.class.getResource("/editor/keywords/diff.css").toExternalForm();
                if (!scene.getStylesheets().contains(diffStyleUrl)) {
                    scene.getStylesheets().add(diffStyleUrl);
                }
            }
        });
    }
}
