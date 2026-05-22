package com.flashgif.developer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeveloperKeyRepository extends JpaRepository<DeveloperKey, UUID> {

    Optional<DeveloperKey> findByKeyHash(byte[] keyHash);

    List<DeveloperKey> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
