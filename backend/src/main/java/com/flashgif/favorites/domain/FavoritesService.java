package com.flashgif.favorites.domain;

import com.flashgif.media.domain.Media;
import com.flashgif.media.domain.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FavoritesService {

    private final FavoriteRepository favoriteRepository;
    private final MediaRepository mediaRepository;

    /** Idempotent: re-favoriting an existing favorite is a no-op (count not double-bumped). */
    @Transactional
    public void favorite(UUID userId, UUID mediaId) {
        if (favoriteRepository.existsByUserIdAndMediaId(userId, mediaId)) {
            return;
        }
        Media media = mediaRepository.findByIdForUpdate(mediaId)        // pessimistic lock
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Media not found"));
        favoriteRepository.save(Favorite.of(userId, mediaId));
        media.setFavoriteCount(media.getFavoriteCount() + 1);
        mediaRepository.save(media);
    }

    /** Idempotent: unfavoriting a non-favorite is a no-op. */
    @Transactional
    public void unfavorite(UUID userId, UUID mediaId) {
        long removed = favoriteRepository.deleteByUserIdAndMediaId(userId, mediaId);
        if (removed == 0) return;
        mediaRepository.findByIdForUpdate(mediaId).ifPresent(m -> {
            m.setFavoriteCount(Math.max(0, m.getFavoriteCount() - 1));
            mediaRepository.save(m);
        });
    }

    @Transactional(readOnly = true)
    public Page<Favorite> list(UUID userId, Pageable pageable) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
