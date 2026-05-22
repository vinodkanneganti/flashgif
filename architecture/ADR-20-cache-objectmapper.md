# ADR-20: Cache `ObjectMapper` with default typing (separate from web mapper)

**Status:** Accepted (post-bug)
**Date:** 2026-05-22 (Slice Web 1)
**Tags:** backend, cache, serialization

## Context
The original `CacheConfig` initialised `GenericJackson2JsonRedisSerializer` with the default web `ObjectMapper`. Generic type info was lost on round-trip: `List<MediaSummary>` came back as `List<LinkedHashMap>`, blowing up the subsequent HTTP serialization with `IllegalArgumentException: object is not an instance of declaring class`. See ADR-67 for the bug post-mortem.

## Decision
A dedicated cache `ObjectMapper` with `activateDefaultTyping(...)` + a `BasicPolymorphicTypeValidator` allowing `com.flashgif.*`, `java.util.*`, `java.time.*`. Wired into `GenericJackson2JsonRedisSerializer` and used only by `RedisCacheManager`. The web `ObjectMapper` (used by Spring MVC for HTTP responses) stays untouched.

## Rationale
- Default typing embeds `@class` markers so generic types survive serialize/deserialize — that's the whole reason for two mappers.
- The validator prevents the well-known polymorphic-deserialization gadget vulnerabilities by allow-listing safe packages.
- Keeping the cache mapper separate from the web mapper means `@class` never leaks into the public API.
- The fix is small and localised; alternative (manual type tokens at every cache call site) was much uglier.

## Consequences
- Two `ObjectMapper` beans coexist; wiring uses qualifiers to keep them straight.
- Cache invalidation on type renames or moves requires `FLUSHDB` — old `@class` markers won't resolve. Documented in the runbook.
- New domain packages must be added to the type validator if they're ever cached.
