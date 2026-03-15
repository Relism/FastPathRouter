package dev.relism.fpr.core;

import dev.relism.fpr.core.dsl.StringRouteParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterConcurrencyTest {

    @Test
    void testConcurrentMatchingDoesNotCorruptState() throws InterruptedException {
        int threadCount = 20;
        int iterationsPerThread = 100_000;

        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/users"), "USERS_LIST");
        builder.add(StringRouteParser.parse("/users/{id}"), "USER_DETAIL");
        builder.add(StringRouteParser.parse("/users/{id}/orders/{orderId}"), "USER_ORDER");
        builder.add(StringRouteParser.parse("/static/pre-{x}-suf"), "STATIC_MIXED");
        builder.add(StringRouteParser.parse("/assets/**"), "ASSETS_CATCH_ALL");

        FastPathRouter<ByteView, String> router = builder.compile();

        byte[] path1 = "/users".getBytes(StandardCharsets.US_ASCII);
        byte[] path2 = "/users/123".getBytes(StandardCharsets.US_ASCII);
        byte[] path3 = "/users/123/orders/abc".getBytes(StandardCharsets.US_ASCII);
        byte[] path4 = "/static/pre-xyz-suf".getBytes(StandardCharsets.US_ASCII);
        byte[] path5 = "/assets/css/style.css".getBytes(StandardCharsets.US_ASCII);
        byte[] path6 = "/not-found".getBytes(StandardCharsets.US_ASCII);

        ByteView[] views = new ByteView[] {
                new ByteArrayView(path1),
                new ByteArrayView(path2),
                new ByteArrayView(path3),
                new ByteArrayView(path4),
                new ByteArrayView(path5),
                new ByteArrayView(path6)
        };

        String[] expectedHandlers = new String[] {
                "USERS_LIST",
                "USER_DETAIL",
                "USER_ORDER",
                "STATIC_MIXED",
                "ASSETS_CATCH_ALL",
                null
        };

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            futures.add(executor.submit((Callable<Integer>) () -> {
                MatchResult<String> result = new MatchResult<>(builder.maxParamCount(), 64);
                int localFailures = 0;

                startLatch.await(); // wait for all threads to be ready

                for (int j = 0; j < iterationsPerThread; j++) {
                    int pathIndex = (j + threadIndex) % views.length;
                    ByteView view = views[pathIndex];
                    String expectedHandler = expectedHandlers[pathIndex];

                    result.reset();
                    int routeId = router.match(view, result);

                    if (expectedHandler == null) {
                        if (routeId != FastPathRouter.NO_MATCH || result.handler() != null) {
                            localFailures++;
                        }
                    } else {
                        if (routeId == FastPathRouter.NO_MATCH || !expectedHandler.equals(result.handler())) {
                            localFailures++;
                        }
                    }
                }
                endLatch.countDown();
                return localFailures;
            }));
        }

        // Fire!
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        assertTrue(completed, "Concurrency test timed out");

        int totalFailures = 0;
        for (Future<Integer> future : futures) {
            try {
                totalFailures += future.get();
            } catch (Exception e) {
                totalFailures++;
            }
        }

        executor.shutdownNow();

        // 0 failures means every matching attempt returned the correct route
        assertEquals(0, totalFailures, "There were mismatched routes during concurrent access (race condition)");
    }

    private static final class ByteArrayView implements ByteView {
        private final byte[] bytes;

        private ByteArrayView(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int length() {
            return bytes.length;
        }

        @Override
        public byte byteAt(int index) {
            return bytes[index];
        }

        @Override
        public boolean supportsLong() {
            return false;
        }

        @Override
        public long longAt(int index) {
            throw new UnsupportedOperationException();
        }
    }
}
