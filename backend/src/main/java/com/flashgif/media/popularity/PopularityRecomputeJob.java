package com.flashgif.media.popularity;

import com.flashgif.infra.outbox.OutboxPublisher;
import com.flashgif.media.domain.Media;
import com.flashgif.media.domain.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Periodic batch: recomputes {@code media.popularity} for recently-changed
 * media and writes outbox events so the search index picks up the new score.
 *
 * <p>Formula: {@code log(1 + favorite_count*3 + view_count) * exp(-age_days/7)}
 * — favorites are weighted 3x views, then decayed exponentially over a 7-day
 * half-life. Tweak the weights per feedback once we have analytics.
 *
 * <p>Avoids per-favorite write amplification: a single user spamming favorites
 * doesn't bombard the outbox / ES.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class PopularityRecomputeJob {

    /** Look back further than the schedule period so we catch anything missed across restarts. */
    private static final Duration LOOKBACK = Duration.ofMinutes(15);
    private static final float MIN_CHANGE = 0.01f;  // ignore tiny float drift, avoid outbox churn

    private final MediaRepository mediaRepository;
    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${flashgif.popularity.recompute-ms:300000}",     // 5 min
               initialDelayString = "${flashgif.popularity.initial-delay-ms:60000}") // 1 min after boot
    @Transactional
    public void recompute() {
        OffsetDateTime since = OffsetDateTime.now().minus(LOOKBACK);
        List<Media> candidates = mediaRepository.findPublishedUpdatedSince(since);
        if (candidates.isEmpty()) return;

        int touched = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (Media m : candidates) {
            float old = m.getPopularity();
            float fresh = score(m, now);
            if (Math.abs(old - fresh) < MIN_CHANGE) continue;

            m.setPopularity(fresh);
            mediaRepository.save(m);
            outboxPublisher.publish("media", m.getId(), "media.updated",
                    Map.of("mediaId", m.getId().toString()));
            touched++;
        }
        if (touched > 0) {
            log.info("Popularity recompute: {} of {} media updated", touched, candidates.size());
        }
    }

    static float score(Media m, OffsetDateTime now) {
        double weighted = m.getFavoriteCount() * 3.0 + m.getViewCount();
        double base = Math.log(1.0 + weighted);
        double ageDays = Duration.between(m.getCreatedAt(), now).toMinutes() / (60.0 * 24.0);
        double decay = Math.exp(-ageDays / 7.0);
        return (float) (base * decay);
    }
}
