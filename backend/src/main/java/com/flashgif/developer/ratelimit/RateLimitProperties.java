package com.flashgif.developer.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("flashgif.developer")
public record RateLimitProperties(
        int requestsPerMinute,
        int burstCapacity
) {
    public RateLimitProperties {
        if (requestsPerMinute <= 0) requestsPerMinute = 60;
        if (burstCapacity <= 0)     burstCapacity = requestsPerMinute;
    }
}
