package com.flashgif.developer.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records developer-API usage to Redis and (debounced) bumps
 * {@code developer_keys.last_used_at} so the dashboard "last seen" is accurate
 * without hammering the DB.
 *
 * <p>Key layout: {@code dev:usage:{keyId}:{yyyyMMdd} → counter}, 35-day TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageRecorder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration COUNTER_TTL = Duration.ofDays(35);
    private static final Duration LAST_USED_DEBOUNCE = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final DeveloperKeyRepository keyRepository;
    private final ConcurrentHashMap<UUID, Long> lastUsedFlushNanos = new ConcurrentHashMap<>();

    public void record(UUID keyId) {
        String key = counterKey(keyId, LocalDate.now());
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First INCR for this day → set the TTL once.
            redis.expire(key, COUNTER_TTL);
        }
        maybeFlushLastUsed(keyId);
    }

    public long countForDay(UUID keyId, LocalDate day) {
        String val = redis.opsForValue().get(counterKey(keyId, day));
        return val == null ? 0L : Long.parseLong(val);
    }

    static String counterKey(UUID keyId, LocalDate day) {
        return "dev:usage:" + keyId + ":" + day.format(DAY);
    }

    /** Debounced DB update — at most one write per LAST_USED_DEBOUNCE per key. */
    private void maybeFlushLastUsed(UUID keyId) {
        long now = System.nanoTime();
        Long prev = lastUsedFlushNanos.get(keyId);
        if (prev != null && (now - prev) < LAST_USED_DEBOUNCE.toNanos()) return;
        lastUsedFlushNanos.put(keyId, now);
        try {
            updateLastUsed(keyId);
        } catch (RuntimeException ex) {
            log.warn("Failed to update last_used_at for key {}: {}", keyId, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateLastUsed(UUID keyId) {
        keyRepository.findById(keyId).ifPresent(k -> {
            k.setLastUsedAt(OffsetDateTime.now());
            keyRepository.save(k);
        });
    }
}
