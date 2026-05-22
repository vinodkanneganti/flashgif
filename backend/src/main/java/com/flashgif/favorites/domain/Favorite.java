package com.flashgif.favorites.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "favorites")
@IdClass(FavoriteId.class)
@Getter @Setter
@NoArgsConstructor
public class Favorite {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public static Favorite of(UUID userId, UUID mediaId) {
        Favorite f = new Favorite();
        f.userId = userId;
        f.mediaId = mediaId;
        return f;
    }
}
