package com.flashgif.media.domain;

public enum MediaStatus {
    PROCESSING, PUBLISHED, REJECTED;

    public String dbValue() { return name().toLowerCase(); }

    public static MediaStatus fromDb(String v) { return MediaStatus.valueOf(v.toUpperCase()); }
}
