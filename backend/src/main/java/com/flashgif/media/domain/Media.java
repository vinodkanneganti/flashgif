package com.flashgif.media.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "media")
@Getter @Setter
@NoArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Populated by {@link com.flashgif.media.domain.PublishService} from the
     *  current authenticated user (Slice 3+). Null for media seeded before auth. */
    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** Stored as lowercase {@code gif|sticker}. Mapped via String column to keep the CHECK constraint authoritative. */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "content_rating", nullable = false, length = 8)
    private String contentRating = ContentRating.G.dbValue();

    @Column(nullable = false, length = 16)
    private String status = MediaStatus.PUBLISHED.dbValue();

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "favorite_count", nullable = false)
    private long favoriteCount;

    @Column(nullable = false)
    private float popularity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rendition_urls", columnDefinition = "jsonb")
    private Map<String, String> renditionUrls;

    private Integer width;
    private Integer height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "media_tags", joinColumns = @JoinColumn(name = "media_id"))
    @Column(name = "tag", length = 64, nullable = false)
    private Set<String> tags = new HashSet<>();

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public MediaType getMediaType()       { return MediaType.fromDb(type); }
    public ContentRating getRating()      { return ContentRating.fromDb(contentRating); }
    public MediaStatus getMediaStatus()   { return MediaStatus.fromDb(status); }
}
