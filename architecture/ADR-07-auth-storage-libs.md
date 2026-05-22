# ADR-07: JJWT 0.12 + AWS SDK v2 (lib choices)

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, security, storage

## Context
Two third-party libs sit on the critical path: the JWT library (used by the auth slice) and the S3 client (used by the upload slice). Each has multiple credible options.

## Decision
- **JJWT 0.12.6** for JWT issue/parse.
- **AWS SDK v2** (`software.amazon.awssdk:s3` + `s3-presigner`) for S3 / MinIO access. No `spring-cloud-aws`.

## Rationale
- JJWT 0.12 is the modern, fluent API. The older `jjwt:jjwt` artifact is deprecated; 0.12 split into `jjwt-api`/`jjwt-impl`/`jjwt-jackson` for cleaner classpath isolation.
- AWS SDK v2 is the supported lineage (v1 is in maintenance). Builder-style clients, non-blocking variants available, first-class presigner support.
- `spring-cloud-aws` brings autoconfiguration we don't need and ties us to its release cadence; a hand-built `S3Client` bean is ~10 lines and gives full control over the MinIO endpoint + path-style addressing.

## Consequences
- Auth slice (`JwtService`) uses JJWT's `Jwts.builder().signWith(key, Jwts.SIG.HS256)` style — slightly different from older code samples.
- Storage slice (`StorageConfig`) owns S3 client construction; switching from MinIO to real S3 is an endpoint+region change, no code change.
- Both libs follow semver; minor upgrades are safe within the pinned majors.
