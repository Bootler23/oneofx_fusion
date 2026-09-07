package com.oneofx.fusion.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Client-side guard for the published Bitpanda Fusion request limits. */
final class FusionRateLimiter {
    private static final long WINDOW_MS = 60_000L;
    // Keep a 5% safety margin below the official limits.
    private static final int GLOBAL_LIMIT = 950;
    private static final int MARKET_DATA_LIMIT = 228;
    private static final int CREATE_ORDER_LIMIT = 285;

    private final Deque<Long> globalRequests = new ArrayDeque<>();
    private final Deque<Long> marketDataRequests = new ArrayDeque<>();
    private final Deque<Long> createOrderRequests = new ArrayDeque<>();

    synchronized void acquire(String method, String path) {
        Deque<Long> category = categoryQueue(method, path);
        int categoryLimit = categoryLimit(method, path);

        while (true) {
            long now = System.currentTimeMillis();
            evictExpired(globalRequests, now);
            if (category != null) evictExpired(category, now);

            long globalWait = waitTime(globalRequests, GLOBAL_LIMIT, now);
            long categoryWait = category == null ? 0L : waitTime(category, categoryLimit, now);
            long waitMs = Math.max(globalWait, categoryWait);
            if (waitMs <= 0L) {
                globalRequests.addLast(now);
                if (category != null) category.addLast(now);
                return;
            }

            try {
                wait(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FusionApiException("Interrupted while waiting for Fusion API rate limit", e);
            }
        }
    }

    private Deque<Long> categoryQueue(String method, String path) {
        if ("POST".equals(method) && "/v1/account/orders".equals(path)) return createOrderRequests;
        if (path.startsWith("/v1/time") || path.startsWith("/v1/tickers") || path.startsWith("/v1/pairs")
                || path.startsWith("/v1/orderbook/") || path.startsWith("/v1/candles/")) {
            return marketDataRequests;
        }
        return null;
    }

    private static int categoryLimit(String method, String path) {
        if ("POST".equals(method) && "/v1/account/orders".equals(path)) return CREATE_ORDER_LIMIT;
        return MARKET_DATA_LIMIT;
    }

    private static void evictExpired(Deque<Long> requests, long now) {
        long threshold = now - WINDOW_MS;
        while (!requests.isEmpty() && requests.peekFirst() <= threshold) requests.removeFirst();
    }

    private static long waitTime(Deque<Long> requests, int limit, long now) {
        if (requests.size() < limit) return 0L;
        return Math.max(1L, requests.peekFirst() + WINDOW_MS - now + 10L);
    }
}
