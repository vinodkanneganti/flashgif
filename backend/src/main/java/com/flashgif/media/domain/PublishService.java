package com.flashgif.media.domain;

import com.flashgif.infra.outbox.OutboxPublisher;
import com.flashgif.media.api.dto.MetadataRequest;
import com.flashgif.media.api.dto.PublishResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Turns a READY upload into a published {@link Media} row + emits the outbox
 * event that the existing Search slice consumes for indexing.
 */
@Service
@RequiredArgsConstructor
public class PublishService {

    private final MediaUploadRepository uploadRepository;
    private final MediaRepository mediaRepository;
    private final OutboxPublisher outboxPublisher;

    @Transactional
    public PublishResponse publish(MetadataRequest req) {
        MediaUpload upload = uploadRepository.findById(req.uploadId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Upload not found"));

        if (upload.getStatus() == UploadStatus.PUBLISHED && upload.getMediaId() != null) {
            // Idempotency: re-submitting metadata for an already-published upload
            // returns the existing media id rather than 409 or duplicate.
            return new PublishResponse(upload.getMediaId(), upload.getId());
        }
        if (upload.getStatus() != UploadStatus.READY) {
            throw new ResponseStatusException(CONFLICT,
                    "Upload is not READY (current state: " + upload.getStatus() + ")");
        }
        if (!req.type().equalsIgnoreCase(upload.getOriginalContentType().startsWith("image/")
                ? req.type() : req.type())) {
            // No-op guard placeholder — content-type vs declared 'type' coherence
            // checks land when we add proper rules.
        }

        Media media = new Media();
        media.setTitle(req.title());
        media.setDescription(req.description());
        media.setType(req.type().toLowerCase());
        media.setContentRating(req.contentRating() == null ? ContentRating.G.dbValue() : req.contentRating().toLowerCase());
        media.setStatus(MediaStatus.PUBLISHED.dbValue());
        media.setWidth(upload.getWidth());
        media.setHeight(upload.getHeight());
        media.setRenditionUrls(upload.getRenditionUrls());
        media.setUploaderId(upload.getUploaderId());   // carry through from the upload row
        if (req.tags() != null) media.getTags().addAll(req.tags());
        Media saved = mediaRepository.save(media);

        upload.setMediaId(saved.getId());
        upload.transitionTo(UploadStatus.PUBLISHED);
        uploadRepository.save(upload);

        outboxPublisher.publish("media", saved.getId(), "media.published",
                Map.of("mediaId", saved.getId().toString()));

        return new PublishResponse(saved.getId(), upload.getId());
    }
}
