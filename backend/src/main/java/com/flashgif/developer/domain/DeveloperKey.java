package com.flashgif.developer.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "developer_keys")
@Getter @Setter
@NoArgsConstructor
public class DeveloperKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private byte[] keyHash;

    @Column(nullable = false, length = 16)
    private String prefix;

    @Column(nullable = false, length = 16)
    private String status = DeveloperKeyStatus.ACTIVE.dbValue();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return DeveloperKeyStatus.ACTIVE.dbValue().equals(status);
    }
}
