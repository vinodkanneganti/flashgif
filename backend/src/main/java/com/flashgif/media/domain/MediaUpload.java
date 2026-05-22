package com.flashgif.media.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "media_uploads")
@Getter @Setter
@NoArgsConstructor
public class MediaUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "original_content_type", nullable = false, length = 64)
    private String originalContentType;

    @Column(name = "original_size", nullable = false)
    private long originalSize;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UploadStatus status = UploadStatus.AWAITING_UPLOAD;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    private Integer width;
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rendition_urls", columnDefinition = "jsonb")
    private Map<String, String> renditionUrls;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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

    public void transitionTo(UploadStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Illegal upload state transition: " + status + " → " + next);
        }
        this.status = next;
    }
}
