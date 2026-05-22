package com.flashgif.media.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    /** Pessimistic SELECT … FOR UPDATE — use when bumping per-row counters. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id = :id")
    Optional<Media> findByIdForUpdate(@Param("id") UUID id);

    /** Published media whose counters/state may have shifted since the last popularity sweep. */
    @Query("SELECT m FROM Media m WHERE m.status = 'published' AND m.updatedAt >= :since")
    List<Media> findPublishedUpdatedSince(@Param("since") OffsetDateTime since);

    // ----- Slice 5: channel feeds -----

    Page<Media> findByUploaderIdAndStatusOrderByCreatedAtDesc(
            UUID uploaderId, String status, Pageable pageable);

    long countByUploaderIdAndStatus(UUID uploaderId, String status);

    List<Media> findTop5ByUploaderIdAndStatusOrderByFavoriteCountDescCreatedAtDesc(
            UUID uploaderId, String status);
}
