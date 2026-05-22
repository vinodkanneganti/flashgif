package com.flashgif.media.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    /** Returns the canonical S3 key for the original upload file. */
    public String originalKey(UUID uploadId, String filename) {
        return "uploads/" + uploadId + "/" + sanitize(filename);
    }

    /** Returns the canonical S3 key for a rendition (e.g. mp4, webp, gif, poster). */
    public String renditionKey(UUID uploadId, String kind, String ext) {
        return "renditions/" + uploadId + "/" + kind + "." + ext;
    }

    /** Generates a short-lived presigned PUT URL for the browser to upload to. */
    public PresignedPut presignUpload(String s3Key, String contentType, long contentLength) {
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(s3Key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.presignExpirySeconds()))
                .putObjectRequest(putReq)
                .build());
        return new PresignedPut(presigned.url().toString(),
                java.time.OffsetDateTime.now().plusSeconds(props.presignExpirySeconds()));
    }

    /** Confirms the object exists in S3 and returns its actual size. */
    public long headSize(String s3Key) {
        HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                .bucket(props.bucket()).key(s3Key).build());
        return head.contentLength();
    }

    /** Downloads an object to a local path (worker uses this to fetch originals). */
    public void download(String s3Key, Path target) {
        s3.getObject(GetObjectRequest.builder().bucket(props.bucket()).key(s3Key).build(), target);
    }

    /** Uploads a local file (a rendition produced by FFmpeg) to S3 and returns the public URL. */
    public String uploadFile(String s3Key, Path source, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(s3Key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(source));
        return publicUrl(s3Key);
    }

    /** Best-effort public URL. For real prod, this would be a CDN URL. */
    public String publicUrl(String s3Key) {
        return props.endpoint() + "/" + props.bucket() + "/" + s3Key;
    }

    private static String sanitize(String filename) {
        String safe = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isBlank() ? "file" : safe;
    }

    public record PresignedPut(String url, java.time.OffsetDateTime expiresAt) {}
}
