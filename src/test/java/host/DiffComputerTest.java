package host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiffComputerTest {

    @Test
    void simpleHunkDetection() {
        // No real git repo needed for computeLineHunks
        DiffComputer dc = new DiffComputer(null, null);
        String a = "line1\nline2\nline3\n";
        String b = "line1\nLINE2\nline3\n";
        List<DiffComputer.Hunk> hunks = dc.computeLineHunks(a, b);
        assertThat(hunks).isNotEmpty();
    }

    @Test
    void alignForSideBySideAddsPlaceholderRowsForInsertion() {
        // Arrange
        DiffComputer dc = new DiffComputer(null, null);
        String oldText = "a\nb\nc\n";
        String newText = "a\nb\nX\nc\n";
        List<DiffComputer.Hunk> hunks = dc.computeLineHunks(oldText, newText);

        // Act
        DiffComputer.AlignedDiff aligned = dc.alignForSideBySide(oldText, newText, hunks);

        // Assert
        assertThat(aligned.hunkRowStarts()).containsExactly(2);
        assertThat(aligned.rows()).hasSize(5);
        DiffComputer.AlignedRow insertedRow = aligned.rows().get(2);
        assertThat(insertedRow.leftPlaceholder()).isTrue();
        assertThat(insertedRow.rightPlaceholder()).isFalse();
        assertThat(insertedRow.leftText()).isEmpty();
        assertThat(insertedRow.rightText()).isEqualTo("X");
        assertThat(insertedRow.changed()).isTrue();
    }

    @Test
    void alignForSideBySideAddsPlaceholderRowsForDeletion() {
        // Arrange
        DiffComputer dc = new DiffComputer(null, null);
        String oldText = "a\nb\nc\n";
        String newText = "a\nc\n";
        List<DiffComputer.Hunk> hunks = dc.computeLineHunks(oldText, newText);

        // Act
        DiffComputer.AlignedDiff aligned = dc.alignForSideBySide(oldText, newText, hunks);

        // Assert
        assertThat(aligned.hunkRowStarts()).containsExactly(1);
        assertThat(aligned.rows()).hasSize(4);
        DiffComputer.AlignedRow deletedRow = aligned.rows().get(1);
        assertThat(deletedRow.leftPlaceholder()).isFalse();
        assertThat(deletedRow.rightPlaceholder()).isTrue();
        assertThat(deletedRow.leftText()).isEqualTo("b");
        assertThat(deletedRow.rightText()).isEmpty();
        assertThat(deletedRow.changed()).isTrue();
    }
}
