package com.flashgif.media.domain;

public enum ContentRating {
    G, PG, PG13, R;

    public String dbValue() { return name().toLowerCase(); }

    public static ContentRating fromDb(String v) { return ContentRating.valueOf(v.toUpperCase()); }
}
