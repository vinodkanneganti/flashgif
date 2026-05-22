# ADR-32: Per-rendition non-fatal transcode

**Status:** Accepted (post-bug; see ADR-72)
**Date:** 2026-05-21 (Slice 2, hardened post-Bug 72)
**Tags:** backend, media, resilience

## Context
The original Slice 2 transcode pipeline ran all renditions (webp, gif, mp4, poster) as a single sequential block and failed the whole job on any FFmpeg error. ADR-72 then surfaced the reality: Homebrew FFmpeg ships without `libwebp` by default, so every dev-machine upload was DLQ-ing on a missing encoder.

## Decision
Each rendition runs inside a `tryRendition(...)` wrapper. A per-encoder failure logs a warning and skips that output. The job only fails (and routes to DLQ) when *all* renditions fail.

## Rationale
- Realistic FFmpeg installs have varying encoder coverage; one missing codec shouldn't lose the whole publish.
- The web client already falls back webp → gif → poster in `MediaCard`, so a missing rendition is invisible to users.
- "Best-effort per output" is the correct default for a transcoder pipeline — fail-fast is for systems where partial outputs are worse than none.

## Consequences
- Adding a new rendition (HD, vertical, AVIF) inherits the resilience for free — same wrapper, no new error handling.
- Ops alerting must be on "all-renditions-failed" rate, NOT per-rendition failure rate — the latter is now a noisy signal.
- A media row can be `PUBLISHED` with a strict subset of renditions present; consumers must handle missing keys in `rendition_urls`.
- Local dev no longer requires `brew reinstall ffmpeg --with-libwebp` to ship uploads.
