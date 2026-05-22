/**
 * Search module: keyword search, trending, and autocomplete suggestions.
 * Owns the Elasticsearch {@code media} index and the outbox-driven indexer
 * that keeps it in sync with Postgres.
 *
 * <p>Public entry points: {@code SearchController}. No other module may
 * depend on classes in {@code search.index} or {@code search.sync}.
 */
package com.flashgif.search;
