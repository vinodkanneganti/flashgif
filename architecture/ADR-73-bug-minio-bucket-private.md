# ADR-73: MinIO bucket private by default → renditions 403

**Status:** Accepted
**Date:** 2026-05-23 (Backend Slice 2, latent — surfaced in Web 3)
**Tags:** bug, ops, storage

## Context
**Symptom:** Upload completed, the transcode job ran cleanly, the redirect landed on `/channels/[username]`, and the new media row appeared in the API response. But every `<img>` showed a broken-image icon. `curl` of the rendition URL: `HTTP/1.1 403 Forbidden`.

Root cause (latent since Slice 2): the bucket created by `BucketBootstrapper` got MinIO's default policy, which is private. Renditions are CDN-style public content — anyone with the URL should be able to GET them — but the bucket policy was never relaxed to allow that. Originals correctly stay private. Slice 2's smoke tests exercised the presigned upload PUT path (which works against a private bucket) but never tested an unauthenticated GET of a rendition URL.

## Decision
Added a `putBucketPolicy` call in `BucketBootstrapper.run()` that allows `s3:GetObject` from `Principal: *` on the `renditions/*` prefix only. Originals (`uploads/*`) remain fully private — the only reads are presigned URLs the backend mints for uploader-tagged endpoints. See [ADR-33](ADR-33-s3-prefix-policy.md) for the prefix-scoped policy design.

Recovery: applied the same policy out-of-band via `mc anonymous set-json` so existing renditions became visible immediately. Next backend restart re-applies it idempotently.

## Rationale
- Splitting the bucket into "public prefix" + "private prefix" is one bucket policy instead of two buckets — simpler ops, same security boundary.
- Could have used two buckets (public-renditions, private-uploads). Cleaner conceptually, but doubles the bootstrap + monitoring + lifecycle-policy code.
- Could have signed every rendition URL. Operationally worse — every CDN edge would need to call back for signed URLs, killing the "cache forever" property.

## Consequences
- Production envs follow the same pattern: bucket policy with prefix-scoped public read on renditions, CDN (CloudFront / Cloudflare) in front with same policy on the origin. The CDN caches eagerly; origin reads are 1-per-rendition.
- New rule for `BucketBootstrapper`-style code: assume "private by default" and assert the public-read policy explicitly. Don't trust the storage system's default to match your intent.
- Sibling to [ADR-70](ADR-70-bug-s3-key-not-null.md), [ADR-71](ADR-71-bug-dispatch-race.md), [ADR-72](ADR-72-bug-libwebp-missing.md) — all latent in Slice 2, all surfaced in <30 min once the web upload modal exercised the full pipeline.
