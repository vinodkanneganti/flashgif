package com.flashgif.media.dev;

import com.flashgif.infra.outbox.OutboxPublisher;
import com.flashgif.media.domain.ContentRating;
import com.flashgif.media.domain.Media;
import com.flashgif.media.domain.MediaRepository;
import com.flashgif.media.domain.MediaStatus;
import com.flashgif.media.domain.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inserts a handful of sample media rows on first {@code local}-profile boot so
 * the Search slice can be exercised before the Media upload slice exists.
 * Idempotent: skipped if the media table is already non-empty.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
class MediaSeeder implements ApplicationRunner {

    private final MediaRepository mediaRepository;
    private final OutboxPublisher outboxPublisher;

    private record Sample(String title, String description, MediaType type, Set<String> tags,
                          long views, long favorites, float popularity) {}

    private static final List<Sample> SAMPLES = List.of(
            new Sample("Happy dance",          "Tiny excited dance loop",       MediaType.GIF,
                    Set.of("happy", "dance", "celebrate"),          12_400,  890, 9.2f),
            new Sample("Sad rain",             "Cartoon raincloud",             MediaType.STICKER,
                    Set.of("sad", "rain", "mood"),                   3_100,  120, 4.1f),
            new Sample("Excited cat",          "Cat with sparkles",             MediaType.GIF,
                    Set.of("cat", "happy", "sparkle", "cute"),      45_900, 6_700, 12.7f),
            new Sample("Mind blown",           "Reaction GIF, exploding head",  MediaType.GIF,
                    Set.of("reaction", "mindblown", "wow"),         28_300, 3_900, 10.4f),
            new Sample("Slow clap",            "Polite applause loop",          MediaType.GIF,
                    Set.of("clap", "applause", "reaction"),          8_700,  410, 5.9f),
            new Sample("Eye roll",             "Big dramatic eye roll",         MediaType.GIF,
                    Set.of("eyeroll", "annoyed", "reaction"),       16_500, 1_200, 7.3f),
            new Sample("Confetti party",       "Confetti burst, festive",       MediaType.STICKER,
                    Set.of("party", "confetti", "celebrate"),       21_200, 2_800, 9.8f),
            new Sample("Friday vibes",         "Weekend mood loop",             MediaType.GIF,
                    Set.of("friday", "weekend", "mood", "dance"),   33_800, 4_500, 11.1f),
            new Sample("Coffee time",          "Steaming mug loop",             MediaType.STICKER,
                    Set.of("coffee", "morning", "tired"),            6_400,  300, 4.7f),
            new Sample("Thumbs up",            "Approval reaction",             MediaType.GIF,
                    Set.of("thumbsup", "approve", "reaction", "yes"), 18_900, 2_100, 8.6f)
    );

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (mediaRepository.count() > 0) {
            log.info("Media table non-empty — skipping seed");
            return;
        }
        log.info("Seeding {} sample media rows", SAMPLES.size());

        for (Sample s : SAMPLES) {
            Media m = new Media();
            m.setTitle(s.title());
            m.setDescription(s.description());
            m.setType(s.type().dbValue());
            m.setContentRating(ContentRating.G.dbValue());
            m.setStatus(MediaStatus.PUBLISHED.dbValue());
            m.setViewCount(s.views());
            m.setFavoriteCount(s.favorites());
            m.setPopularity(s.popularity());
            m.setWidth(480);
            m.setHeight(360);
            m.setRenditionUrls(Map.of(
                    "gif",    "https://example.invalid/" + slug(s.title()) + ".gif",
                    "mp4",    "https://example.invalid/" + slug(s.title()) + ".mp4",
                    "webp",   "https://example.invalid/" + slug(s.title()) + ".webp",
                    "poster", "https://example.invalid/" + slug(s.title()) + ".jpg"));
            m.getTags().addAll(s.tags());
            Media saved = mediaRepository.save(m);

            outboxPublisher.publish("media", saved.getId(), "media.published",
                    Map.of("mediaId", saved.getId().toString()));
        }
        log.info("Seed complete; outbox poller will index to Elasticsearch within ~2s");
    }

    private static String slug(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
