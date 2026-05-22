package com.flashgif.favorites.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, CollectionItemId> {

    Page<CollectionItem> findByCollectionIdOrderByAddedAtDesc(UUID collectionId, Pageable pageable);

    boolean existsByCollectionIdAndMediaId(UUID collectionId, UUID mediaId);

    long deleteByCollectionIdAndMediaId(UUID collectionId, UUID mediaId);
}
