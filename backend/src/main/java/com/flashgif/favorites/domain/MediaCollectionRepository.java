package com.flashgif.favorites.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaCollectionRepository extends JpaRepository<MediaCollection, UUID> {

    List<MediaCollection> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<MediaCollection> findByOwnerIdAndIsPublicTrueOrderByCreatedAtDesc(UUID ownerId);
}
