package com.flashgif.media.domain;

import com.flashgif.media.api.dto.UploadRequest;
import com.flashgif.media.api.dto.UploadResponse;
import com.flashgif.media.api.dto.UploadStatusResponse;
import com.flashgif.media.storage.StorageService;
import com.flashgif.media.transcode.TranscodeDispatcher;
import com.flashgif.media.transcode.TranscodeMessage;
import com.flashgif.users.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime",
            "image/gif", "image/webp"
    );

    private final MediaUploadRepository uploadRepository;
    private final StorageService storage;
    private final TranscodeDispatcher dispatcher;

    @Transactional
    public UploadResponse create(UploadRequest req) {
        if (!ALLOWED_CONTENT_TYPES.contains(req.contentType())) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Unsupported content type: " + req.contentType());
        }

        // Generate the id up-front so we can build the S3 key and persist it on
        // the first INSERT (s3_key is NOT NULL). Avoids the pre-flush dance.
        UUID id = UUID.randomUUID();
        String s3Key = storage.originalKey(id, req.filename());

        MediaUpload upload = new MediaUpload();
        upload.setId(id);
        upload.setOriginalFilename(req.filename());
        upload.setOriginalContentType(req.contentType());
        upload.setOriginalSize(req.size());
        upload.setS3Key(s3Key);
        UserPrincipal.currentUserId().ifPresent(upload::setUploaderId);
        upload = uploadRepository.save(upload);

        StorageService.PresignedPut presigned = storage.presignUpload(
                s3Key, req.contentType(), req.size());

        return new UploadResponse(upload.getId(), s3Key, presigned.url(), presigned.expiresAt());
    }

    @Transactional
    public void markUploaded(UUID uploadId) {
        MediaUpload upload = require(uploadId);
        if (upload.getStatus() != UploadStatus.AWAITING_UPLOAD) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Upload is in state " + upload.getStatus() + ", cannot complete");
        }

        long actualSize;
        try {
            actualSize = storage.headSize(upload.getS3Key());
        } catch (RuntimeException ex) {
            log.warn("S3 HEAD failed for {}: {}", uploadId, ex.getMessage());
            upload.transitionTo(UploadStatus.FAILED);
            upload.setErrorMessage("Object not found in storage: " + ex.getMessage());
            uploadRepository.save(upload);
            throw new ResponseStatusException(BAD_REQUEST, "Upload object not present in storage");
        }

        upload.setOriginalSize(actualSize);
        upload.transitionTo(UploadStatus.UPLOADED);
        uploadRepository.save(upload);

        // The Rabbit dispatch must happen AFTER this transaction commits.
        // Otherwise the worker (in the same JVM) can pull the message and try
        // to read the row before the INSERT/UPDATE is visible to its own tx,
        // seeing stale AWAITING_UPLOAD state and failing the state-machine
        // transition. Registering an afterCommit hook serialises us correctly.
        final TranscodeMessage msg = new TranscodeMessage(
                upload.getId(), upload.getS3Key(),
                upload.getOriginalContentType(), upload.getOriginalSize());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatch(msg);
            }
        });
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse status(UUID uploadId) {
        MediaUpload u = require(uploadId);
        return new UploadStatusResponse(
                u.getId(), u.getStatus(), u.getErrorMessage(),
                u.getWidth(), u.getHeight(), u.getDurationMs(),
                u.getRenditionUrls(), u.getMediaId());
    }

    private MediaUpload require(UUID uploadId) {
        return uploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Upload not found: " + uploadId));
    }
}
