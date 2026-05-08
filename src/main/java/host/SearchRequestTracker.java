package host;

class SearchRequestTracker {

    private long activeRequestId;

    long beginNewRequest() {
        return ++activeRequestId;
    }

    void cancelCurrentAndOlder() {
        activeRequestId++;
    }

    boolean isActive(long requestId) {
        return requestId == activeRequestId;
    }
}
