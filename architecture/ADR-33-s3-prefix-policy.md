# ADR-33: Per-prefix S3 bucket policy (renditions public, uploads private)

**Status:** Accepted (post-bug; see ADR-73)
**Date:** 2026-05-21 (Slice 2, codified post-Bug 73)
**Tags:** backend, storage, security

## Context
Renditions are CDN-style content — anyone with the URL should GET them, ideally through a CDN that caches aggressively. Original uploads are private uploader assets that the system reads on demand (re-transcode, retention). MinIO and S3 both default new buckets to private, so first dev-run uploads 403'd on render (ADR-73).

## Decision
Single bucket `flashgif-media`, one bucket policy that allows `s3:GetObject` from `*` only on `renditions/*`. `uploads/*` stays private; the backend signs read URLs as needed. `BucketBootstrapper` applies the policy idempotently on startup so dev environments converge automatically.

## Rationale
- One bucket, one policy keeps the mental model and lifecycle rules in one place.
- Per-prefix ACLs are S3's purpose-built primitive for this split — no need for two buckets and the duplicated config.
- CDN config in prod points at `renditions/*` only — no risk of leaking originals through a misconfigured cache.

## Consequences
- The bootstrapper runs on every backend start; idempotent — re-applying the same policy is a no-op against S3.
- Adding a new public prefix is a policy edit, not a new bucket — keeps IaC small.
- If we ever need signed URLs for renditions (e.g., paid content), the policy gets tightened and a `RenditionUrlSigner` is added; today's public path is the simplest correct thing.
- Local MinIO and prod S3 use the same policy JSON, so a bug found in one is fixed in the other.
