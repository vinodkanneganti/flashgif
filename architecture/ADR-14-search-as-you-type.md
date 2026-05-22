# ADR-14: `search_as_you_type` for autocomplete

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, search

## Context
Autocomplete on `/api/v1/search/suggestions` needs sub-100ms responses on partial prefixes ("ca" → cat, cake, capybara…). Elasticsearch offers two canonical approaches: the dedicated `completion` suggester (FST-backed, fastest, narrow API) and the `search_as_you_type` field type (n-gram subfields, plain `multi_match`).

## Decision
Map `title.suggest` as `search_as_you_type`. Autocomplete queries are a `bool_prefix` `multi_match` across the auto-generated `_2gram` / `_3gram` subfields.

## Rationale
- `search_as_you_type` is a normal field — the query is just `multi_match`, no special suggester DSL.
- The `_2gram` / `_3gram` subfields are generated automatically; no analyzer config to maintain.
- Easier to evolve: adding a filter (e.g., "suggestions for stickers only") is a `bool` clause, same as any search query.
- The `completion` suggester's latency edge doesn't matter at our cardinality; the API simplicity does.

## Consequences
- Slightly larger index than `completion` would produce (n-gram subfields aren't free).
- `SuggestionService` shares its query builder with the main search code — one mental model.
- Suggestions are cached in Redis (5-minute TTL) keyed on lowercased prefix, so per-request ES load is bounded.
