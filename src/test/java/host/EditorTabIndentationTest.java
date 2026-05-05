package editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

@ExtendWith(ApplicationExtension.class)
class EditorTabIndentationTest {

    private CodeArea codeArea;

    @Start
    private void start(Stage stage) {
        codeArea = new CodeArea();
        stage.setScene(new Scene(codeArea, 600, 400));
        stage.show();
    }

    private void handleIndentation(boolean outdent) {
        int start = codeArea.getSelection().getStart();
        int end = codeArea.getSelection().getEnd();
        String text = codeArea.getText();

        if (start == end) {
            if (!outdent) {
                codeArea.insertText(start, "    ");
            } else {
                String textBefore = text.substring(0, start);
                int lastNewline = textBefore.lastIndexOf('\n');
                int lineStart = lastNewline + 1;
                String lineText = text.substring(lineStart, start);

                int spacesToRemove = Math.min(4, lineText.length());
                int actualToRemove = 0;
                for (int i = 0; i < spacesToRemove && i < lineText.length(); i++) {
                    if (lineText.charAt(i) == ' ' || lineText.charAt(i) == '\t') {
                        actualToRemove++;
                    } else {
                        break;
                    }
                }
                if (actualToRemove > 0) {
                    codeArea.deleteText(lineStart, lineStart + actualToRemove);
                }
            }
            return;
        }

        String[] lines = text.split("\n", -1);
        int startLine = 0;
        int currentPos = 0;
        for (int i = 0; i < lines.length; i++) {
            int lineEnd = currentPos + lines[i].length();
            if (start >= currentPos && start <= lineEnd) {
                startLine = i;
                break;
            }
            currentPos = lineEnd + 1;
        }

        int endLine = 0;
        currentPos = 0;
        for (int i = 0; i < lines.length; i++) {
            int lineEnd = currentPos + lines[i].length();
            if (end > currentPos && end <= lineEnd + 1) {
                endLine = i;
                break;
            }
            currentPos = lineEnd + 1;
        }

        StringBuilder newText = new StringBuilder(text);
        int offsetAdjustment = 0;

        for (int line = startLine; line <= endLine; line++) {
            int lineStartOffset = 0;
            for (int i = 0; i < line; i++) {
                lineStartOffset += lines[i].length() + 1;
            }

            String currentLineText = lines[line];

            if (outdent) {
                int spacesToRemove = Math.min(4, currentLineText.length());
                int actualToRemove = 0;
                for (int i = 0; i < spacesToRemove && i < currentLineText.length(); i++) {
                    if (currentLineText.charAt(i) == ' ' || currentLineText.charAt(i) == '\t') {
                        actualToRemove++;
                    } else {
                        break;
                    }
                }
                if (actualToRemove > 0) {
                    int actualStart = lineStartOffset + offsetAdjustment;
                    newText.delete(actualStart, actualStart + actualToRemove);
                    offsetAdjustment -= actualToRemove;
                }
            } else {
                int actualStart = lineStartOffset + offsetAdjustment;
                newText.insert(actualStart, "    ");
                offsetAdjustment += 4;
            }
        }

        codeArea.replaceText(0, text.length(), newText.toString());

        int newStart = 0;
        for (int i = 0; i < startLine; i++) {
            String[] newLines = newText.toString().split("\n", -1);
            newStart += newLines[i].length() + 1;
        }

        int newEnd = newStart;
        for (int i = startLine; i <= endLine; i++) {
            String[] newLines = newText.toString().split("\n", -1);
            if (i < newLines.length) {
                newEnd += newLines[i].length() + 1;
            }
        }

        codeArea.selectRange(newStart, newEnd);
    }

    @Test
    void tab_inserts_four_spaces() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "hello");
            codeArea.moveTo(5);
            handleIndentation(false);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("hello    ");
    }

    @Test
    void shift_tab_removes_leading_spaces() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "        hello");
            codeArea.moveTo(13);
            handleIndentation(true);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("    hello");
    }

    @Test
    void shift_tab_does_nothing_when_no_leading_whitespace() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "hello");
            codeArea.moveTo(5);
            handleIndentation(true);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("hello");
    }

    @Test
    void shift_tab_removes_partial_indent() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "  hello");
            codeArea.moveTo(7);
            handleIndentation(true);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("hello");
    }

    @Test
    void shift_tab_removes_from_multiline_selection() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "    line1\n    line2\n    line3");
            codeArea.selectRange(0, codeArea.getLength());
            handleIndentation(true);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void tab_indents_multiline_selection() {
        Platform.runLater(() -> {
            codeArea.replaceText(0, 0, "line1\nline2\nline3");
            codeArea.selectRange(0, codeArea.getLength());
            handleIndentation(false);
        });
        waitForFxEvents();

        assertThat(codeArea.getText()).isEqualTo("    line1\n    line2\n    line3");
    }
}
