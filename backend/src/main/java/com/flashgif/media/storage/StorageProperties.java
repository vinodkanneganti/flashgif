package com.flashgif.media.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("flashgif.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        long maxUploadBytes,
        int presignExpirySeconds
) {
    public StorageProperties {
        if (maxUploadBytes <= 0) maxUploadBytes = 100L * 1024 * 1024;     // 100 MB default
        if (presignExpirySeconds <= 0) presignExpirySeconds = 900;        // 15 min default
    }
}
