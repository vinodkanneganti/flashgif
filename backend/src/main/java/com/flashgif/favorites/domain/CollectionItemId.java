package com.flashgif.favorites.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CollectionItemId implements Serializable {
    private UUID collectionId;
    private UUID mediaId;

    public CollectionItemId() {}
    public CollectionItemId(UUID collectionId, UUID mediaId) {
        this.collectionId = collectionId;
        this.mediaId = mediaId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollectionItemId other)) return false;
        return Objects.equals(collectionId, other.collectionId)
            && Objects.equals(mediaId, other.mediaId);
    }
    @Override public int hashCode() { return Objects.hash(collectionId, mediaId); }
}
