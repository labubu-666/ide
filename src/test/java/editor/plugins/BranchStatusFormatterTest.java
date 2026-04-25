package editor.plugins;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import editor.plugins.versioncontrol.git.BranchStatusFormatter;

/**
 * Tests for BranchStatusFormatter class.
 * Tests the formatting logic for branch status display.
 */
public class BranchStatusFormatterTest {
    
    private BranchStatusFormatter formatter;
    
    @BeforeEach
    public void setUp() {
        formatter = new BranchStatusFormatter();
    }
    
    @Test
    public void testFormatCleanBranch() {
        String result = formatter.format("main", false, false, false, 0, 0);
        assertThat(result).isEqualTo("main");
    }
    
    @Test
    public void testFormatWithStagedChanges() {
        String result = formatter.format("main", true, false, false, 0, 0);
        assertThat(result).isEqualTo("main+");
    }
    
    @Test
    public void testFormatWithUnstagedChanges() {
        String result = formatter.format("main", false, true, false, 0, 0);
        assertThat(result).isEqualTo("main*");
    }
    
    @Test
    public void testFormatWithBothStagedAndUnstaged() {
        String result = formatter.format("main", true, true, false, 0, 0);
        assertThat(result).isEqualTo("main+*");
    }
    
    @Test
    public void testFormatWithAheadCommits() {
        String result = formatter.format("main", false, false, true, 2, 0);
        assertThat(result).isEqualTo("main ↑2");
    }
    
    @Test
    public void testFormatWithBehindCommits() {
        String result = formatter.format("main", false, false, true, 0, 3);
        assertThat(result).isEqualTo("main ↓3");
    }
    
    @Test
    public void testFormatWithBothAheadAndBehind() {
        String result = formatter.format("main", false, false, true, 2, 3);
        assertThat(result).isEqualTo("main ↑2↓3");
    }
    
    @Test
    public void testFormatWithEverything() {
        String result = formatter.format("main", true, true, true, 1, 2);
        assertThat(result).isEqualTo("main+* ↑1↓2");
    }
    
    @Test
    public void testFormatWithNoRemoteTracking() {
        // Should not show arrows when hasRemote is false, even with ahead/behind counts
        String result = formatter.format("main", false, false, false, 5, 3);
        assertThat(result).isEqualTo("main");
    }
    
    @Test
    public void testFormatWithRemoteButInSync() {
        // Should not show arrows when ahead and behind are both 0
        String result = formatter.format("main", false, false, true, 0, 0);
        assertThat(result).isEqualTo("main");
    }
    
    @Test
    public void testFormatWithNullBranchName() {
        String result = formatter.format(null, false, false, false, 0, 0);
        assertThat(result).isEqualTo("");
    }
    
    @Test
    public void testFormatWithEmptyBranchName() {
        String result = formatter.format("", false, false, false, 0, 0);
        assertThat(result).isEqualTo("");
    }
    
    @Test
    public void testFormatWithFeatureBranch() {
        String result = formatter.format("feature/new-feature", true, false, true, 3, 0);
        assertThat(result).isEqualTo("feature/new-feature+ ↑3");
    }
    
    @Test
    public void testFormatTooltipCleanBranch() {
        String result = formatter.formatTooltip("main", false, false, false, 0, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("No remote tracking branch");
    }
    
    @Test
    public void testFormatTooltipWithStagedChanges() {
        String result = formatter.formatTooltip("main", true, false, false, 0, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("Staged changes (+)");
    }
    
    @Test
    public void testFormatTooltipWithUnstagedChanges() {
        String result = formatter.formatTooltip("main", false, true, false, 0, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("Unstaged changes (*)");
    }
    
    @Test
    public void testFormatTooltipWithBothChanges() {
        String result = formatter.formatTooltip("main", true, true, false, 0, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("Staged changes (+)")
            .contains("Unstaged changes (*)");
    }
    
    @Test
    public void testFormatTooltipWithRemoteAhead() {
        String result = formatter.formatTooltip("main", false, false, true, 2, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("↑ 2 commit(s) to push");
    }
    
    @Test
    public void testFormatTooltipWithRemoteBehind() {
        String result = formatter.formatTooltip("main", false, false, true, 0, 3);
        assertThat(result)
            .contains("Branch: main")
            .contains("↓ 3 commit(s) to pull");
    }
    
    @Test
    public void testFormatTooltipWithRemoteAheadAndBehind() {
        String result = formatter.formatTooltip("main", false, false, true, 2, 3);
        assertThat(result)
            .contains("Branch: main")
            .contains("↑ 2 commit(s) to push")
            .contains("↓ 3 commit(s) to pull");
    }
    
    @Test
    public void testFormatTooltipWithRemoteInSync() {
        String result = formatter.formatTooltip("main", false, false, true, 0, 0);
        assertThat(result)
            .contains("Branch: main")
            .contains("Up to date with remote");
    }
    
    @Test
    public void testFormatTooltipWithEverything() {
        String result = formatter.formatTooltip("main", true, true, true, 1, 2);
        assertThat(result)
            .contains("Branch: main")
            .contains("Staged changes (+)")
            .contains("Unstaged changes (*)")
            .contains("↑ 1 commit(s) to push")
            .contains("↓ 2 commit(s) to pull");
    }
    
    @Test
    public void testFormatTooltipWithNullBranchName() {
        String result = formatter.formatTooltip(null, false, false, false, 0, 0);
        assertThat(result).isEqualTo("No branch");
    }
    
    @Test
    public void testFormatTooltipWithEmptyBranchName() {
        String result = formatter.formatTooltip("", false, false, false, 0, 0);
        assertThat(result).isEqualTo("No branch");
    }
}
