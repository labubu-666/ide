package utils;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BenchmarkUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkUtils.class);

    private BenchmarkUtils() {
    }

    public static <T> T benchmark(String label, Supplier<T> action) {
        long startedAt = System.nanoTime();
        try {
            return action.get();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.info("[Benchmark] {} took {}ms", label, elapsedMs);
        }
    }

    public static void benchmark(String label, Runnable action) {
        benchmark(label, () -> {
            action.run();
            return null;
        });
    }
}
