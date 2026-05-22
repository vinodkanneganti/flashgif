# ADR-67: Cache serializer lost generic type info

**Status:** Accepted
**Date:** 2026-05-22 (Backend Slice 1, surfaced in Web 1)
**Tags:** bug, cache

## Context
**Symptom:** `/api/v1/trending` and `/api/v1/search/suggestions` started returning `500 IllegalArgumentException: object is not an instance of declaring class` after the first cache hit. First request (cache miss) worked; second request (cache hit) blew up in the HTTP serializer.

Root cause: `@Cacheable("trending")` and `@Cacheable("suggestions")` return `List<MediaSummary>` and `List<Suggestion>`. The default `GenericJackson2JsonRedisSerializer` — initialised with our standard web `ObjectMapper` — does not embed `@class` type hints in cached values. On read, Jackson sees a JSON array and deserialises to `List<LinkedHashMap>`. The HTTP serializer then tries to call `MediaSummary` record accessors on a `LinkedHashMap`, which fails.

## Decision
Dedicated cache `ObjectMapper` with `activateDefaultTyping(PolymorphicTypeValidator, EVERYTHING, WRAPPER_ARRAY)` configured via a `BasicPolymorphicTypeValidator` that allows only `com.flashgif.`, `java.util.`, `java.time.`. Cache writes now embed `@class` markers; reads round-trip to the correct concrete types. The web-facing `ObjectMapper` stays untouched — `@class` does NOT leak into HTTP responses. See [ADR-20](ADR-20-cache-objectmapper.md) for the broader two-mapper decision.

Operational note: rolling out the fix requires a backend restart AND `redis-cli FLUSHDB` to evict the type-tag-less entries. Otherwise reads of stale entries still fail.

## Rationale
- The type validator (allow-list of packages) is the security mitigation against default-typing's deserialisation gadget risk. Untyped default-typing-everything is a known RCE pattern; the validator narrows it to packages we control.
- Could have used `Jackson2JsonRedisSerializer<T>` per cache (typed at construction), which avoids `@class` entirely. Rejected because we'd need one serializer bean per cache value type — fine for two caches, painful at twenty.
- The fix is one of those Spring-Redis-Jackson-glue bugs you only hit once. Recording it here so the next person doesn't burn a day on it.

## Consequences
- Every new `@Cacheable` value type must be in an allow-listed package (or the allow-list extended). Compile passes; deserialization throws at runtime if mismatched.
- This bug masked itself in development by the way `@Cacheable` works: the first call to a cached method bypasses the read path (it's a miss → method runs → write to cache). Only the second call exercises the deserializer. Local "click around once, looks fine" testing won't catch it.
- Triggered [ADR-66](ADR-66-bug-isr-cached-empty.md) (ISR cached the empty `[]` that this bug produced upstream). Two bugs, one bad-data window.
