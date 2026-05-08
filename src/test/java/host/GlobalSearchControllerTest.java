package host;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import search.SearchMatch;
import search.SearchService;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchControllerTest {

    @Test
    void prepareQueryReturnsTrueOnlyForNonEmptyQueries(@TempDir Path root) {
        GlobalSearchController controller = new GlobalSearchController(new FixedSearchService(root, List.of()));

        boolean empty = controller.prepareQuery("");
        boolean nullQuery = controller.prepareQuery(null);
        boolean nonEmpty = controller.prepareQuery("needle");

        assertThat(empty).isFalse();
        assertThat(nullQuery).isFalse();
        assertThat(nonEmpty).isTrue();
    }

    @Test
    void executeSearchReturnsResultsAndStatusText(@TempDir Path root) {
        List<SearchMatch> matches = List.of(
                new SearchMatch(root.resolve("a.txt"), 1, "needle one", 0, 6),
                new SearchMatch(root.resolve("b.txt"), 2, "needle two", 0, 6));
        GlobalSearchController controller = new GlobalSearchController(new FixedSearchService(root, matches));

        controller.prepareQuery("needle");
        GlobalSearchController.SearchRun run = controller.beginSearch("needle");
        GlobalSearchController.SearchResult result = controller.executeSearch(run);

        assertThat(result.ignored()).isFalse();
        assertThat(result.hasError()).isFalse();
        assertThat(result.results()).hasSize(2);
        assertThat(result.statusText()).isEqualTo("2 matches");
        assertThat(controller.shouldApply(run, "needle")).isTrue();
    }

    @Test
    void newerQueryInvalidatesOlderRun(@TempDir Path root) {
        GlobalSearchController controller = new GlobalSearchController(new FixedSearchService(root, List.of()));

        controller.prepareQuery("first");
        GlobalSearchController.SearchRun firstRun = controller.beginSearch("first");
        controller.prepareQuery("second");
        GlobalSearchController.SearchResult result = controller.executeSearch(firstRun);

        assertThat(result.ignored()).isTrue();
        assertThat(controller.shouldApply(firstRun, "first")).isFalse();
    }

    @Test
    void executeSearchReturnsErrorWhenSearchFails(@TempDir Path root) {
        GlobalSearchController controller = new GlobalSearchController(new FailingSearchService(root));

        controller.prepareQuery("needle");
        GlobalSearchController.SearchRun run = controller.beginSearch("needle");
        GlobalSearchController.SearchResult result = controller.executeSearch(run);

        assertThat(result.ignored()).isFalse();
        assertThat(result.hasError()).isTrue();
        assertThat(result.errorText()).isEqualTo("Error: boom");
    }

    private static final class FixedSearchService extends SearchService {
        private final List<SearchMatch> matches;

        private FixedSearchService(Path rootPath, List<SearchMatch> matches) {
            super(rootPath);
            this.matches = matches;
        }

        @Override
        public List<SearchMatch> search(String query) {
            return matches;
        }
    }

    private static final class FailingSearchService extends SearchService {

        private FailingSearchService(Path rootPath) {
            super(rootPath);
        }

        @Override
        public List<SearchMatch> search(String query) throws IOException {
            throw new IOException("boom");
        }
    }
}
