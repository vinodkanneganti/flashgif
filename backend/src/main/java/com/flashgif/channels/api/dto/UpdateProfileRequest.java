package com.flashgif.channels.api.dto;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Map;

/**
 * All fields optional — clients PATCH only what they want to change. To clear
 * a field, pass an empty string (services treat blank/null the same way).
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50)                                 String displayName,
        @Size(max = 4000)                                        String bio,
        @URL @Size(max = 255)                                    String websiteUrl,
        @URL @Size(max = 500)                                    String avatarUrl,
        @URL @Size(max = 500)                                    String bannerUrl,
        /** Whitelisted keys: twitter, instagram, tiktok, youtube, github. */
        Map<String, String>                                      socialLinks
) {}
