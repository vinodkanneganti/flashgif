package com.flashgif.users.domain;

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
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Always stored lowercase — normalize via {@link #setEmail(String)} or
     *  {@link com.flashgif.users.domain.UserService#normalizeEmail(String)}. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase(java.util.Locale.ROOT);
    }

    /** Public, URL-safe handle. Unique, format {@code [a-zA-Z0-9_]{3,30}}. */
    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, length = 16)
    private String status = UserStatus.ACTIVE.dbValue();

    // ----- Public channel profile (Slice 5) -----

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    /** Whitelisted keys validated at the service layer (twitter, instagram, tiktok, youtube, github). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "jsonb")
    private Map<String, String> socialLinks;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
}
