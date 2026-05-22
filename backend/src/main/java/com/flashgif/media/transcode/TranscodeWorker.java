package com.flashgif.media.transcode;

import com.flashgif.media.domain.MediaUpload;
import com.flashgif.media.domain.MediaUploadRepository;
import com.flashgif.media.domain.UploadStatus;
import com.flashgif.media.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@link TranscodeMessage} from RabbitMQ. For each upload:
 *   1. mark PROCESSING
 *   2. download original to temp dir
 *   3. ffprobe → store width/height/duration
 *   4. produce mp4, animated webp, gif, poster jpeg
 *   5. upload renditions to S3
 *   6. mark READY with rendition_urls
 *
 * On exception the listener re-throws → Rabbit redelivers up to default
 * attempts, then routes to the DLQ defined in {@link RabbitConfig}. We also
 * persist FAILED state so {@code GET /status/{id}} can report something useful.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TranscodeWorker {

    private final MediaUploadRepository uploadRepository;
    private final StorageService storage;
    private final FFmpegRunner ffmpeg;

    @RabbitListener(queues = RabbitConfig.TRANSCODE_QUEUE)
    public void onMessage(TranscodeMessage message) throws Exception {
        UUID uploadId = message.uploadId();
        log.info("Transcode start: {}", uploadId);

        markStatus(uploadId, UploadStatus.PROCESSING, null);

        Path workDir = Files.createTempDirectory("flashgif-" + uploadId + "-");
        try {
            Path original = workDir.resolve("original");
            storage.download(message.s3Key(), original);

            FFmpegRunner.ProbeResult probe = ffmpeg.probe(original);

            // Each rendition is independent — a missing encoder (e.g. Homebrew
            // FFmpeg ships without libwebp_anim by default) shouldn't fail the
            // whole job. We log + skip and let the others through.
            Map<String, String> urls = new LinkedHashMap<>();
            tryRendition(urls, "mp4",    "mp4",  "video/mp4",
                    workDir, uploadId, dst -> ffmpeg.toMp4(original, dst));
            tryRendition(urls, "webp",   "webp", "image/webp",
                    workDir, uploadId, dst -> ffmpeg.toAnimatedWebp(original, dst));
            tryRendition(urls, "gif",    "gif",  "image/gif",
                    workDir, uploadId, dst -> ffmpeg.toGif(original, dst));
            tryRendition(urls, "poster", "jpg",  "image/jpeg",
                    workDir, uploadId, dst -> ffmpeg.toPosterJpeg(original, dst));

            if (urls.isEmpty()) {
                throw new IllegalStateException("All renditions failed for " + uploadId);
            }

            markReady(uploadId, probe, urls);
            log.info("Transcode complete: {} ({} renditions: {})",
                    uploadId, urls.size(), String.join(",", urls.keySet()));
        } catch (Exception ex) {
            log.error("Transcode failed for {}: {}", uploadId, ex.getMessage(), ex);
            markStatus(uploadId, UploadStatus.FAILED, ex.getMessage());
            throw ex;  // re-throw → Rabbit redelivery / DLQ
        } finally {
            deleteRecursively(workDir);
        }
    }

    @Transactional
    void markStatus(UUID uploadId, UploadStatus next, String error) {
        MediaUpload u = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("Upload not found: " + uploadId));
        if (next == UploadStatus.FAILED && u.getStatus() == UploadStatus.FAILED) return;
        u.transitionTo(next);
        u.setErrorMessage(error);
        uploadRepository.save(u);
    }

    @Transactional
    void markReady(UUID uploadId, FFmpegRunner.ProbeResult probe, Map<String, String> urls) {
        MediaUpload u = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("Upload not found: " + uploadId));
        u.setWidth(probe.width());
        u.setHeight(probe.height());
        u.setDurationMs(probe.durationMs());
        u.setRenditionUrls(urls);
        u.transitionTo(UploadStatus.READY);
        u.setCompletedAt(java.time.OffsetDateTime.now());
        uploadRepository.save(u);
    }

    /** Functional interface for "produce file at dst". */
    @FunctionalInterface
    private interface RenditionProducer {
        void produce(Path dst) throws Exception;
    }

    /**
     * Run one rendition. If it throws (e.g. missing FFmpeg encoder), log and
     * skip — the URL just doesn't appear in the final map. MediaCard falls
     * back webp → gif → poster, so partial output is fine.
     */
    private void tryRendition(Map<String, String> urls, String kind, String ext, String contentType,
                              Path workDir, UUID uploadId, RenditionProducer producer) {
        Path dst = workDir.resolve("output-" + kind + "." + ext);
        try {
            producer.produce(dst);
            String url = storage.uploadFile(storage.renditionKey(uploadId, kind, ext), dst, contentType);
            urls.put(kind, url);
        } catch (Exception ex) {
            log.warn("Rendition '{}' failed for {} (skipping): {}", kind, uploadId, ex.getMessage());
        }
    }

    private static void deleteRecursively(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}
    }
}
