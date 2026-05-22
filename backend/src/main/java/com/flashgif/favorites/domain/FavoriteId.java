package com.flashgif.favorites.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite-key class for {@link Favorite}. Must be Serializable + equals/hashCode. */
public class FavoriteId implements Serializable {
    private UUID userId;
    private UUID mediaId;

    public FavoriteId() {}
    public FavoriteId(UUID userId, UUID mediaId) {
        this.userId = userId;
        this.mediaId = mediaId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(mediaId, other.mediaId);
    }
    @Override public int hashCode() { return Objects.hash(userId, mediaId); }
}
