package editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TabOperationsTest {

    @Test
    void closeRemovesTheTargetTab() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.close(tabs, "b");
        assertThat(tabs).isEqualTo(List.of("a", "c"));
    }

    @Test
    void closeFirstTab() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.close(tabs, "a");
        assertThat(tabs).isEqualTo(List.of("b", "c"));
    }

    @Test
    void closeLastTab() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.close(tabs, "c");
        assertThat(tabs).isEqualTo(List.of("a", "b"));
    }

    @Test
    void closeNonExistentTabIsNoOp() {
        List<String> tabs = new ArrayList<>(List.of("a", "b"));
        TabOperations.close(tabs, "z");
        assertThat(tabs).isEqualTo(List.of("a", "b"));
    }

    @Test
    void closeOnEmptyListIsNoOp() {
        List<String> tabs = new ArrayList<>();
        TabOperations.close(tabs, "a");
        assertThat(tabs).isEmpty();
    }

    @Test
    void closeOthersKeepsOnlyTarget() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.closeOthers(tabs, "b");
        assertThat(tabs).isEqualTo(List.of("b"));
    }

    @Test
    void closeOthersWhenTargetIsFirst() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.closeOthers(tabs, "a");
        assertThat(tabs).isEqualTo(List.of("a"));
    }

    @Test
    void closeOthersWhenTargetIsLast() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.closeOthers(tabs, "c");
        assertThat(tabs).isEqualTo(List.of("c"));
    }

    @Test
    void closeOthersWithSingleTabIsNoOp() {
        List<String> tabs = new ArrayList<>(List.of("a"));
        TabOperations.closeOthers(tabs, "a");
        assertThat(tabs).isEqualTo(List.of("a"));
    }

    @Test
    void closeOthersWithNonExistentTargetClearsAll() {
        List<String> tabs = new ArrayList<>(List.of("a", "b", "c"));
        TabOperations.closeOthers(tabs, "z");
        assertThat(tabs).isEmpty();
    }

    @Test
    void closeOthersOnEmptyListIsNoOp() {
        List<String> tabs = new ArrayList<>();
        TabOperations.closeOthers(tabs, "a");
        assertThat(tabs).isEmpty();
    }

    @Test
    void closeUsesIdentityNotEquality() {
        String s1 = new String("dup");
        String s2 = new String("dup");
        List<String> tabs = new ArrayList<>(List.of(s1, s2));
        TabOperations.close(tabs, s1);
        assertThat(tabs.size()).isEqualTo(1);
        assertThat(tabs.get(0)).isSameAs(s2);
    }

    @Test
    void closeOthersUsesIdentityNotEquality() {
        String s1 = new String("dup");
        String s2 = new String("dup");
        List<String> tabs = new ArrayList<>(List.of(s1, s2));
        TabOperations.closeOthers(tabs, s1);
        assertThat(tabs.size()).isEqualTo(1);
        assertThat(tabs.get(0)).isSameAs(s1);
    }
}
