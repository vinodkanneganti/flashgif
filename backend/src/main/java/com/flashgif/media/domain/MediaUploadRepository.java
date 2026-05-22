package com.flashgif.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {
}
