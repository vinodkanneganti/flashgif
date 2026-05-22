package com.flashgif.search.domain;

public enum SearchSort {
    RELEVANCE, RECENCY;

    public static SearchSort parse(String raw) {
        if (raw == null || raw.isBlank()) return RELEVANCE;
        try { return SearchSort.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return RELEVANCE; }
    }
}
