package com.flashgif.developer.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key token bucket, in-memory. Single-instance correct; for multi-instance
 * deployments swap this for a Bucket4j-Redis implementation behind the same
 * {@link #tryAcquire} method.
 *
 * <p>Refill model: {@code burstCapacity} tokens, refilled at a rate of
 * {@code requestsPerMinute / 60.0} per second. Calling {@link #tryAcquire}
 * subtracts 1 token; returns false (and rejects the request) when empty.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class TokenBucketLimiter {

    private final RateLimitProperties props;
    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public TryAcquireResult tryAcquire(UUID keyId) {
        Bucket b = buckets.computeIfAbsent(keyId, k -> new Bucket(props.burstCapacity(), perSecondRate()));
        return b.tryAcquire();
    }

    private double perSecondRate() {
        return props.requestsPerMinute() / 60.0;
    }

    public record TryAcquireResult(boolean allowed, long retryAfterSeconds) {
        public static TryAcquireResult allow()                       { return new TryAcquireResult(true,  0); }
        public static TryAcquireResult deny(long retryAfterSeconds)  { return new TryAcquireResult(false, retryAfterSeconds); }
    }

    /** Single bucket: synchronised lazy refill on read. Lock-free reads are fine via synchronized; contention is per-key, not global. */
    static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, double refillPerSecond) {
            this.capacity        = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens          = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized TryAcquireResult tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return TryAcquireResult.allow();
            }
            long retryAfter = (long) Math.ceil((1.0 - tokens) / refillPerSecond);
            return TryAcquireResult.deny(Math.max(1, retryAfter));
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }
}
