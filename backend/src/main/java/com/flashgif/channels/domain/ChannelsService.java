package com.flashgif.channels.domain;

import com.flashgif.channels.api.dto.ChannelResponse;
import com.flashgif.channels.api.dto.UpdateProfileRequest;
import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.media.domain.MediaRepository;
import com.flashgif.users.domain.User;
import com.flashgif.users.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ChannelsService {

    private static final String PUBLISHED = "published";

    /** Whitelisted social-link keys. Anything else is rejected at PATCH time. */
    static final Set<String> ALLOWED_SOCIAL_KEYS =
            Set.of("twitter", "instagram", "tiktok", "youtube", "github");

    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    // ---------------- read ----------------

    @Transactional(readOnly = true)
    public ChannelResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Channel not found"));

        long count = mediaRepository.countByUploaderIdAndStatus(user.getId(), PUBLISHED);
        var top = mediaRepository
                .findTop5ByUploaderIdAndStatusOrderByFavoriteCountDescCreatedAtDesc(user.getId(), PUBLISHED)
                .stream().map(MediaSummary::from).toList();

        return new ChannelResponse(
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getWebsiteUrl(),
                user.getAvatarUrl(),
                user.getBannerUrl(),
                user.getSocialLinks(),
                user.isVerified(),
                count,
                top,
                user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Page<MediaSummary> listMedia(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Channel not found"));
        return mediaRepository
                .findByUploaderIdAndStatusOrderByCreatedAtDesc(user.getId(), PUBLISHED, pageable)
                .map(MediaSummary::from);
    }

    // ---------------- update ----------------

    @Transactional
    public ChannelResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (req.displayName() != null) user.setDisplayName(req.displayName());
        if (req.bio()         != null) user.setBio(blankToNull(req.bio()));
        if (req.websiteUrl()  != null) user.setWebsiteUrl(blankToNull(req.websiteUrl()));
        if (req.avatarUrl()   != null) user.setAvatarUrl(blankToNull(req.avatarUrl()));
        if (req.bannerUrl()   != null) user.setBannerUrl(blankToNull(req.bannerUrl()));
        if (req.socialLinks() != null) user.setSocialLinks(validateSocialLinks(req.socialLinks()));

        userRepository.save(user);
        return getByUsername(user.getUsername());
    }

    private static Map<String, String> validateSocialLinks(Map<String, String> input) {
        if (input.isEmpty()) return null;          // empty map → clear
        Map<String, String> cleaned = new HashMap<>();
        for (var entry : input.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            if (!ALLOWED_SOCIAL_KEYS.contains(key)) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Unknown social link key: '" + entry.getKey() + "'. Allowed: " + ALLOWED_SOCIAL_KEYS);
            }
            String value = entry.getValue() == null ? null : entry.getValue().trim();
            if (value != null && !value.isBlank()) cleaned.put(key, value);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
