package com.flashgif.favorites.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_items")
@IdClass(CollectionItemId.class)
@Getter @Setter
@NoArgsConstructor
public class CollectionItem {

    @Id
    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Id
    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    /** Reserved for a future reorder API; left null in v1. */
    private Integer position;

    @PrePersist
    void onCreate() {
        if (addedAt == null) addedAt = OffsetDateTime.now();
    }

    public static CollectionItem of(UUID collectionId, UUID mediaId) {
        CollectionItem c = new CollectionItem();
        c.collectionId = collectionId;
        c.mediaId = mediaId;
        return c;
    }
}
