package com.flashgif.search.sync;

import com.flashgif.media.domain.Media;
import com.flashgif.media.domain.MediaRepository;
import com.flashgif.search.index.MediaDocument;
import com.flashgif.search.index.MediaSearchRepository;
import com.flashgif.users.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Projects Postgres {@link Media} rows into ES {@link MediaDocument}s.
 *
 * <p>Cross-module note: {@code search} depends on {@code media.domain} read
 * types. Acceptable for a modular monolith; if search were ever extracted to
 * its own service, it would consume a media REST/event contract instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaIndexer {

    private final MediaRepository mediaRepository;
    private final MediaSearchRepository searchRepository;
    private final UserRepository userRepository;

    /** Upsert by id. Returns true on success, false if the source row is gone. */
    public boolean upsert(UUID mediaId) {
        return mediaRepository.findById(mediaId)
                .map(this::project)
                .map(doc -> { searchRepository.save(doc); return true; })
                .orElseGet(() -> {
                    log.warn("Outbox event for media {} but row not found — deleting from ES", mediaId);
                    searchRepository.deleteById(mediaId.toString());
                    return false;
                });
    }

    public void delete(UUID mediaId) {
        searchRepository.deleteById(mediaId.toString());
    }

    private MediaDocument project(Media m) {
        String uploaderName = null;
        if (m.getUploaderId() != null) {
            uploaderName = userRepository.findById(m.getUploaderId())
                    .map(u -> u.getUsername())
                    .orElse(null);
        }
        return MediaDocument.builder()
                .id(m.getId().toString())
                .title(m.getTitle())
                .description(m.getDescription())
                .tags(new ArrayList<>(m.getTags()))
                .type(m.getType())
                .contentRating(m.getContentRating())
                .status(m.getStatus())
                .popularity(m.getPopularity())
                .viewCount(m.getViewCount())
                .favoriteCount(m.getFavoriteCount())
                .createdAt(m.getCreatedAt())
                .uploaderUsername(uploaderName)
                .width(m.getWidth())
                .height(m.getHeight())
                .renditionUrls(m.getRenditionUrls())
                .build();
    }
}
