package host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestTrackerTest {

    @Test
    void firstStartedRequestIsActive() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        long requestId = tracker.beginNewRequest();

        assertThat(tracker.isActive(requestId)).isTrue();
    }

    @Test
    void newestStartedRequestReplacesPreviousActiveRequest() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        long firstRequest = tracker.beginNewRequest();
        long secondRequest = tracker.beginNewRequest();

        assertThat(tracker.isActive(firstRequest)).isFalse();
        assertThat(tracker.isActive(secondRequest)).isTrue();
    }

    @Test
    void cancelCurrentInvalidatesExistingRequests() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        long requestId = tracker.beginNewRequest();
        tracker.cancelCurrentAndOlder();

        assertThat(tracker.isActive(requestId)).isFalse();
    }

    @Test
    void cancelCurrentKeepsFutureRequestsActive() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        tracker.beginNewRequest();
        tracker.cancelCurrentAndOlder();
        long nextRequest = tracker.beginNewRequest();

        assertThat(tracker.isActive(nextRequest)).isTrue();
    }
}
