package host;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    private static final int HUNK_CONTEXT_PADDING_LINES = 5;
    private static final int HUNK_VIEWPORT_MIN_ROWS = 12;

    final Path filePath;
    private final EditorContext ctx;
    private final DiffComputer diffComputer;
    private final managers.GitManager gitManager;

    private final CodeArea leftArea;
    private final CodeArea rightArea;
    private final Label hunkPositionLabel;

    private List<DiffComputer.Hunk> currentHunks = new ArrayList<>();
    private List<DiffComputer.AlignedRow> currentRows = List.of();
    private List<Integer> hunkRowStarts = new ArrayList<>();
    private int currentHunkIndex = -1;

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
        hunkPositionLabel = new Label("0 of 0");
        hunkPositionLabel.setMinWidth(56);
        hunkPositionLabel.setStyle("-fx-text-fill: #777;");

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
        Button prevChange = new Button("▲");
        prevChange.setTooltip(new javafx.scene.control.Tooltip("Previous change"));
        prevChange.setOnAction(e -> navigateHunk(-1));
        Button nextChange = new Button("▼");
        nextChange.setTooltip(new javafx.scene.control.Tooltip("Next change"));
        nextChange.setOnAction(e -> navigateHunk(1));
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
        jumpToFile.setOnAction(e -> ctx.navigator().openFile(filePath));

        return new ToolBar(prevChange, nextChange, hunkPositionLabel, refresh, stageAll, revert, revertSelection, jumpToFile);
    }

    private void updateHunkPositionLabel() {
        int total = currentHunks.size();
        int current = currentHunkIndex >= 0 && currentHunkIndex < total ? currentHunkIndex + 1 : 0;
        hunkPositionLabel.setText(current + " of " + total);
    }

    private void navigateHunk(int direction) {
        if (currentHunks.isEmpty()) return;
        if (currentHunkIndex < 0) {
            currentHunkIndex = direction > 0 ? 0 : currentHunks.size() - 1;
        } else {
            currentHunkIndex = Math.floorMod(currentHunkIndex + direction, currentHunks.size());
        }
        updateHunkPositionLabel();
        int targetRow = resolveTargetRowForHunk(currentHunkIndex);
        moveToAlignedRow(targetRow);
    }

    private int resolveTargetRowForHunk(int hunkIndex) {
        if (hunkIndex < 0 || hunkIndex >= currentHunks.size()) return 0;
        DiffComputer.Hunk hunk = currentHunks.get(hunkIndex);

        int matchByLeft = findChangedRowByLineNumber(true, hunk.oldStart());
        int matchByRight = findChangedRowByLineNumber(false, hunk.newStart());

        if (matchByLeft >= 0 && matchByRight >= 0) {
            return Math.min(matchByLeft, matchByRight);
        }
        if (matchByLeft >= 0) return matchByLeft;
        if (matchByRight >= 0) return matchByRight;
        if (!hunkRowStarts.isEmpty() && hunkIndex < hunkRowStarts.size()) {
            return hunkRowStarts.get(hunkIndex);
        }
        return 0;
    }

    private int findChangedRowByLineNumber(boolean leftSide, int lineNumber) {
        if (lineNumber <= 0 || currentRows.isEmpty()) return -1;
        for (int i = 0; i < currentRows.size(); i++) {
            DiffComputer.AlignedRow row = currentRows.get(i);
            if (!row.changed()) continue;
            int rowLine = leftSide ? row.leftLineNumber() : row.rightLineNumber();
            if (rowLine == lineNumber) return i;
        }
        return -1;
    }

    private void moveToAlignedRow(int row) {
        int maxLeft = Math.max(0, leftArea.getParagraphs().size() - 1);
        int maxRight = Math.max(0, rightArea.getParagraphs().size() - 1);
        int leftTarget = Math.min(Math.max(0, row), maxLeft);
        int rightTarget = Math.min(Math.max(0, row), maxRight);
        int leftMaxTop = Math.max(0, maxLeft - (HUNK_VIEWPORT_MIN_ROWS - 1));
        int rightMaxTop = Math.max(0, maxRight - (HUNK_VIEWPORT_MIN_ROWS - 1));
        int leftTop = Math.min(Math.max(0, leftTarget - HUNK_CONTEXT_PADDING_LINES), leftMaxTop);
        int rightTop = Math.min(Math.max(0, rightTarget - HUNK_CONTEXT_PADDING_LINES), rightMaxTop);

        leftArea.moveTo(leftTarget, 0);
        rightArea.moveTo(rightTarget, 0);
        leftArea.showParagraphAtTop(leftTop);
        rightArea.showParagraphAtTop(rightTop);
    }

    private void applyLineNumbers(List<DiffComputer.AlignedRow> rows) {
        leftArea.setParagraphGraphicFactory(index -> createLineNumberGraphic(rows, index, true));
        rightArea.setParagraphGraphicFactory(index -> createLineNumberGraphic(rows, index, false));
    }

    private void applyPlaceholderParagraphStyles(List<DiffComputer.AlignedRow> rows) {
        int paragraphCount = Math.min(rows.size(), leftArea.getParagraphs().size());
        for (int i = 0; i < paragraphCount; i++) {
            DiffComputer.AlignedRow row = rows.get(i);
            leftArea.setParagraphStyle(i, row.leftPlaceholder()
                    ? Collections.singleton("diff-placeholder-row-left")
                    : Collections.emptyList());
            rightArea.setParagraphStyle(i, row.rightPlaceholder()
                    ? Collections.singleton("diff-placeholder-row-right")
                    : Collections.emptyList());
        }
    }

    private Label createLineNumberGraphic(List<DiffComputer.AlignedRow> rows, int index, boolean leftSide) {
        Label lineNumber = new Label(resolveLineNumberText(rows, index, leftSide));
        lineNumber.setMinWidth(48);
        lineNumber.setPrefWidth(48);
        lineNumber.setAlignment(Pos.CENTER_RIGHT);
        if (index >= 0 && index < rows.size()) {
            DiffComputer.AlignedRow row = rows.get(index);
            boolean placeholder = leftSide ? row.leftPlaceholder() : row.rightPlaceholder();
            if (placeholder) {
                lineNumber.setStyle("-fx-text-fill: #999; -fx-padding: 0 8 0 4; -fx-background-color: #f5f5f5;");
            } else {
                lineNumber.setStyle("-fx-text-fill: #777; -fx-padding: 0 8 0 4;");
            }
        } else {
            lineNumber.setStyle("-fx-text-fill: #777; -fx-padding: 0 8 0 4;");
        }
        return lineNumber;
    }

    private String resolveLineNumberText(List<DiffComputer.AlignedRow> rows, int index, boolean leftSide) {
        if (index < 0 || index >= rows.size()) return "";
        DiffComputer.AlignedRow row = rows.get(index);
        int lineNumber = leftSide ? row.leftLineNumber() : row.rightLineNumber();
        return lineNumber > 0 ? Integer.toString(lineNumber) : "";
    }

    private String buildAlignedSideText(List<DiffComputer.AlignedRow> rows, boolean leftSide) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            DiffComputer.AlignedRow row = rows.get(i);
            sb.append(leftSide ? row.leftText() : row.rightText());
            if (i < rows.size() - 1) sb.append('\n');
        }
        return sb.toString();
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

    private StyleSpans<Collection<String>> buildRowBasedDiffSpans(
            StyleSpans<Collection<String>> syntaxSpans,
            List<DiffComputer.AlignedRow> rows,
            String text,
            boolean isLeft) {
        if (text.isEmpty()) return syntaxSpans;

        int length = text.length();
        List<int[]> diffIntervals = new ArrayList<>();
        int lineOffset = 0;

        for (int i = 0; i < rows.size(); i++) {
            DiffComputer.AlignedRow row = rows.get(i);
            String lineText = isLeft ? row.leftText() : row.rightText();
            int lineStart = lineOffset;
            int lineEnd = lineStart + lineText.length();
            boolean addNewline = i < rows.size() - 1;
            int intervalEnd = addNewline ? Math.min(lineEnd + 1, length) : Math.min(lineEnd, length);

            boolean placeholder = isLeft ? row.leftPlaceholder() : row.rightPlaceholder();
            if (row.changed() && lineStart < intervalEnd) {
                int styleType;
                if (isLeft) {
                    styleType = placeholder ? 2 : 0;
                } else {
                    styleType = placeholder ? 3 : 1;
                }
                diffIntervals.add(new int[]{Math.min(lineStart, length), intervalEnd, styleType});
            }

            lineOffset = lineEnd + (addNewline ? 1 : 0);
        }

        if (diffIntervals.isEmpty()) return syntaxSpans;

        StyleSpansBuilder<Collection<String>> diffSb = new StyleSpansBuilder<>();
        int pos = 0;
        for (int[] iv : diffIntervals) {
            if (iv[0] > pos) diffSb.add(Collections.emptyList(), iv[0] - pos);
            String styleClass = switch (iv[2]) {
                case 0 -> "diff-deleted";
                case 1 -> "diff-added";
                case 2 -> "diff-placeholder-left";
                case 3 -> "diff-placeholder-right";
                default -> "";
            };
            diffSb.add(styleClass.isEmpty() ? Collections.emptyList() : Collections.singleton(styleClass), iv[1] - iv[0]);
            pos = iv[1];
        }
        if (pos < length) diffSb.add(Collections.emptyList(), length - pos);

        StyleSpans<Collection<String>> diffSpans = diffSb.create();
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

            List<DiffComputer.Hunk> hunks = diffComputer.computeLineHunks(head, work);
            currentHunks = hunks;
            currentHunkIndex = -1;
            updateHunkPositionLabel();

            DiffComputer.AlignedDiff aligned = diffComputer.alignForSideBySide(head, work, hunks);
            List<DiffComputer.AlignedRow> rows = aligned.rows();
            currentRows = rows;
            hunkRowStarts = aligned.hunkRowStarts();
            applyLineNumbers(rows);

            String alignedHead = buildAlignedSideText(rows, true);
            String alignedWork = buildAlignedSideText(rows, false);

            leftArea.replaceText(alignedHead);
            rightArea.replaceText(alignedWork);
            applyPlaceholderParagraphStyles(rows);

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
                if (!alignedHead.isEmpty()) {
                    leftSpans = styler.apply(alignedHead);
                }
                if (!alignedWork.isEmpty()) {
                    rightSpans = styler.apply(alignedWork);
                }
            }

            // Merge syntax highlighting with diff highlighting
            if (!hunks.isEmpty()) {
                leftSpans = buildRowBasedDiffSpans(leftSpans, rows, alignedHead, true);
                rightSpans = buildRowBasedDiffSpans(rightSpans, rows, alignedWork, false);
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
                String lspStyleUrl = DiffEditorTab.class.getResource("/host/keywords/lsp.css").toExternalForm();
                if (!scene.getStylesheets().contains(lspStyleUrl)) {
                    scene.getStylesheets().add(lspStyleUrl);
                }
                String diffStyleUrl = DiffEditorTab.class.getResource("/host/keywords/diff.css").toExternalForm();
                if (!scene.getStylesheets().contains(diffStyleUrl)) {
                    scene.getStylesheets().add(diffStyleUrl);
                }
            }

            if (!hunks.isEmpty()) {
                currentHunkIndex = 0;
                updateHunkPositionLabel();
                int firstRow = hunkRowStarts.isEmpty() ? 0 : hunkRowStarts.get(0);
                moveToAlignedRow(firstRow);
            }
        });
    }
}
