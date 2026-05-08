package host;

import java.util.List;
import java.util.Objects;

import search.SearchMatch;
import search.SearchService;

class GlobalSearchController {

    private final SearchService searchService;
    private final SearchRequestTracker requestTracker;

    GlobalSearchController(SearchService searchService) {
        this(searchService, new SearchRequestTracker());
    }

    GlobalSearchController(SearchService searchService, SearchRequestTracker requestTracker) {
        this.searchService = searchService;
        this.requestTracker = requestTracker;
    }

    boolean prepareQuery(String query) {
        requestTracker.cancelCurrentAndOlder();
        return query != null && !query.isEmpty();
    }

    SearchRun beginSearch(String query) {
        return new SearchRun(query, requestTracker.beginNewRequest());
    }

    SearchResult executeSearch(SearchRun run) {
        try {
            List<SearchMatch> results = searchService.search(run.query());
            if (Thread.currentThread().isInterrupted() || !requestTracker.isActive(run.requestId())) {
                return SearchResult.ignoredResult();
            }
            int n = results.size();
            String suffix = n >= SearchService.DEFAULT_MAX_RESULTS ? "+" : "";
            return SearchResult.results(results, n + suffix + " match" + (n == 1 ? "" : "es"));
        } catch (Exception ex) {
            if (!requestTracker.isActive(run.requestId())) {
                return SearchResult.ignoredResult();
            }
            return SearchResult.error("Error: " + ex.getMessage());
        }
    }

    boolean shouldApply(SearchRun run, String currentQuery) {
        return Objects.equals(run.query(), currentQuery) && requestTracker.isActive(run.requestId());
    }

    record SearchRun(String query, long requestId) {
    }

    record SearchResult(List<SearchMatch> results, String statusText, String errorText, boolean ignored) {
        static SearchResult ignoredResult() {
            return new SearchResult(List.of(), null, null, true);
        }

        static SearchResult results(List<SearchMatch> results, String statusText) {
            return new SearchResult(results, statusText, null, false);
        }

        static SearchResult error(String errorText) {
            return new SearchResult(List.of(), null, errorText, false);
        }

        boolean hasError() {
            return errorText != null;
        }
    }
}
