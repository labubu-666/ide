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
}
