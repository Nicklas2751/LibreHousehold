package eu.wiegandt.librehousehold.config;

import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local, in-memory {@link RateLimiter} backed by Bucket4j - no distributed store (e.g. JCache/Redis)
 * is needed, since the current deployment is a single backend instance behind Nginx (see
 * {@code server.forward-headers-strategy} in {@code application.yaml}). Each key gets its own
 * fixed-window bucket: {@code maxAttempts} tokens are available per {@code windowDuration}, all
 * refilled at once once the window elapses.
 */
class Bucket4jRateLimiter implements RateLimiter {

    private final Map<String, Bucket> bucketsByKey = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration windowDuration;

    Bucket4jRateLimiter(int maxAttempts, Duration windowDuration) {
        this.maxAttempts = maxAttempts;
        this.windowDuration = windowDuration;
    }

    @Override
    public boolean isRateLimited(String key) {
        var bucket = bucketsByKey.computeIfAbsent(key, this::newBucket);
        return !bucket.tryConsume(1);
    }

    private Bucket newBucket(String key) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(maxAttempts).refillIntervally(maxAttempts, windowDuration))
                .build();
    }
}
