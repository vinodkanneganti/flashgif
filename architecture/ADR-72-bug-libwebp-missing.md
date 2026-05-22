# ADR-72: Homebrew FFmpeg missing `libwebp` encoder

**Status:** Accepted
**Date:** 2026-05-23 (Backend Slice 2, latent — surfaced in Web 3)
**Tags:** bug, ops

## Context
**Symptom:** `TranscodeWorker` threw `IOException: Process ffmpeg exited 8 ... Unknown encoder 'libwebp_anim' ... Encoder not found`. The whole job aborted on the WebP step — even though three other renditions (poster, mp4, gif) would have succeeded.

Root cause: Homebrew's default `ffmpeg` formula no longer ships with `libwebp` enabled. `ffmpeg -encoders | grep webp` on the dev machine returned nothing. Slice 2 documented `libwebp_anim` as one of four renditions but never verified the host binary actually supported it. CI runs against an `ffmpeg` image that does include `libwebp`, so the bug was specific to the local dev environment — but it was still a real bug because the architecture treated transcode as all-or-nothing.

## Decision
Wrap each rendition call in a `tryRendition(label, () -> ...)` helper. Per-encoder failures log a `WARN` and skip; the other renditions still get produced + saved + uploaded. `markReady()` writes whatever subset succeeded. The job only errors out (routing to DLQ) if *every* rendition fails.

The `<MediaCard>` component already falls back webp → gif → poster, so a missing WebP is invisible to the user.

## Rationale
- Per-rendition resilience is the right architectural shape for any "produce N outputs" pipeline. Future renditions (HD, vertical-9:16, thumbnail) inherit the resilience for free.
- Could have just installed a `libwebp`-enabled `ffmpeg` and called it fixed. Doesn't address the architectural smell — the next missing codec would have produced the same outage.
- Could have probed for available encoders at boot and refused to start if any were missing. Too restrictive — local dev machines often lack codecs that prod has; failing boot would be worse than logging warns.

## Consequences
- See [ADR-32](ADR-32-per-rendition-non-fatal.md) for the canonical "per-rendition non-fatal" policy this bug promoted.
- Dev setup docs should mention "if you want WebP locally, `brew install ffmpeg --with-libwebp` or use the Docker FFmpeg image". Worth adding to `ops/instructions.md` when someone has 5 minutes.
- The `tryRendition` helper is the project's first "graceful per-step degradation" primitive. Worth extending the pattern as we add more multi-output pipelines.
