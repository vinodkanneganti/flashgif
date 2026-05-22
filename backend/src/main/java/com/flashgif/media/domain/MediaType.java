package com.flashgif.media.domain;

public enum MediaType {
    GIF, STICKER;

    public String dbValue() { return name().toLowerCase(); }

    public static MediaType fromDb(String v) { return MediaType.valueOf(v.toUpperCase()); }
}
