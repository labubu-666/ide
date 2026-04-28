package host;

import java.util.List;

public class TabOperations {

    public static <T> void close(List<T> tabs, T tab) {
        tabs.remove(tab);
    }

    public static <T> void closeOthers(List<T> tabs, T tab) {
        tabs.removeIf(t -> t != tab);
    }
}
