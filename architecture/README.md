# FlashGif — Architecture Decision Records

A flat numbered ledger of every meaningful architectural decision and durable
learning the project has accumulated. Generated from [progress.md](../progress.md);
each ADR captures a single decision or bug-fix with its rationale, so the trail
is auditable without re-reading the chronological build log.

## Conventions

- **Status:** Accepted (in production code), Deferred (decided to defer; trigger
  documented), Superseded (replaced by a later ADR).
- **Date / Slice:** when the decision was made.
- **Tags:** backend / web / ops / security / data / observability.
- Each ADR is intentionally short — half a page max. Detail lives in
  `progress.md` and `architecture.md` / `systemdesign.md`.

## Index

### Foundations + tooling

| # | Title | Status |
|---|---|---|
| [00](ADR-00-purpose-and-conventions.md) | Purpose and conventions of this ledger | Accepted |
| [01](ADR-01-modular-monolith.md) | Modular monolith over microservices | Accepted |
| [02](ADR-02-single-gradle-project.md) | Single Gradle project, package-by-feature | Accepted |
| [03](ADR-03-gradle-groovy-dsl.md) | Gradle Groovy DSL over Kotlin DSL | Accepted |
| [04](ADR-04-spring-profiles-api-worker.md) | Spring profiles for API vs Worker (not separate JARs) | Accepted |
| [05](ADR-05-lombok-sparingly.md) | Lombok in, used sparingly | Accepted |
| [06](ADR-06-spring-boot-java-21.md) | Spring Boot 3.3 + Java 21 toolchain | Accepted |
| [07](ADR-07-auth-storage-libs.md) | JJWT 0.12 + AWS SDK v2 (lib choices) | Accepted |
| [08](ADR-08-docker-compose-local-infra.md) | Local infra via docker compose | Accepted |
| [09](ADR-09-ops-runbook.md) | Ops runbook in `ops/instructions.md` | Accepted |

### Data + storage

| # | Title | Status |
|---|---|---|
| [10](ADR-10-postgres-system-of-record.md) | PostgreSQL as system of record | Accepted |
| [11](ADR-11-flyway-owns-schema.md) | Flyway-owned schema; `ddl-auto: validate` | Accepted |
| [12](ADR-12-avoid-vendor-types.md) | Avoid Postgres-vendor column types (`citext`, `inet`) | Accepted |
| [13](ADR-13-elasticsearch-secondary.md) | Elasticsearch as read-only secondary | Accepted |
| [14](ADR-14-search-as-you-type.md) | `search_as_you_type` for autocomplete | Accepted |
| [15](ADR-15-es-alias-bootstrap.md) | Index alias bootstrap (`media → media_v1`) | Accepted |

### Messaging + async

| # | Title | Status |
|---|---|---|
| [16](ADR-16-transactional-outbox.md) | Transactional outbox over CDC | Accepted |
| [17](ADR-17-no-rabbit-on-indexing.md) | No RabbitMQ on the indexing path (DB→ES direct via poller) | Accepted |
| [18](ADR-18-rabbit-over-kafka.md) | RabbitMQ over Kafka for the transcode queue | Accepted |
| [35](ADR-35-publish-after-commit.md) | Publish-after-commit for async dispatch | Accepted |
| [36](ADR-36-outbox-payload-id-only.md) | Outbox payload = `{mediaId}` only | Accepted |
| [37](ADR-37-popularity-recompute-job.md) | Popularity recompute as 5-min batch + outbox | Accepted |

### Cache + Redis

| # | Title | Status |
|---|---|---|
| [19](ADR-19-redis-multipurpose.md) | Redis for cache + rate-limit + dev usage counters | Accepted |
| [20](ADR-20-cache-objectmapper.md) | Cache `ObjectMapper` with default typing (separate from web mapper) | Accepted |
| [21](ADR-21-per-cache-ttl.md) | Per-cache TTL via `RedisCacheManager` | Accepted |

### Security

| # | Title | Status |
|---|---|---|
| [22](ADR-22-jwt-plus-opaque-refresh.md) | JWT access + opaque refresh (mixed mode) | Accepted |
| [23](ADR-23-sha256-tokens.md) | SHA-256 (not BCrypt) for high-entropy tokens | Accepted |
| [24](ADR-24-refresh-rotation-chain.md) | Refresh token rotation chain via `replaced_by` | Accepted |
| [25](ADR-25-two-filter-chains.md) | Two `SecurityFilterChain` beans, scoped by path | Accepted |
| [26](ADR-26-jwt-filter-soft-fail.md) | `UserJwtFilter` soft-fail on invalid token | Accepted |
| [38](ADR-38-404-not-403.md) | 404 (not 403) for ownership/visibility failures | Accepted |
| [49](ADR-49-cookies-via-route-handler.md) | httpOnly cookies via Next.js Route Handler proxy | Accepted |

### Counters + rate limiting

| # | Title | Status |
|---|---|---|
| [27](ADR-27-pessimistic-lock-counters.md) | Pessimistic locking for counter mutations | Accepted |
| [28](ADR-28-in-memory-token-bucket.md) | In-memory token bucket (defer Bucket4j-Redis) | Deferred |

### Media pipeline

| # | Title | Status |
|---|---|---|
| [29](ADR-29-presigned-upload.md) | Direct browser → S3 presigned PUT upload | Accepted |
| [30](ADR-30-explicit-complete-callback.md) | Explicit `/complete` callback over S3 events | Accepted |
| [31](ADR-31-two-table-upload.md) | Two-table upload state machine | Accepted |
| [32](ADR-32-per-rendition-non-fatal.md) | Per-rendition non-fatal transcode | Accepted |
| [33](ADR-33-s3-prefix-policy.md) | Per-prefix S3 bucket policy (renditions public, uploads private) | Accepted |

### API contract

| # | Title | Status |
|---|---|---|
| [34](ADR-34-snake-case-json.md) | snake_case JSON across the public API | Accepted |
| [39](ADR-39-channels-extend-users.md) | Channels extend `users` (no separate creator entity) | Accepted |
| [40](ADR-40-media-summary-ownership.md) | `MediaSummary` lives in `media.api.dto` (cross-module DTO ownership) | Accepted |
| [41](ADR-41-mixed-module-pattern.md) | Mixed-module pattern (server + client API in one file) | Accepted |

### Frontend

| # | Title | Status |
|---|---|---|
| [42](ADR-42-next-app-router.md) | Next.js App Router + Server Components | Accepted |
| [43](ADR-43-hand-roll-next-scaffold.md) | Hand-roll Next.js scaffold (skip `create-next-app`) | Accepted |
| [44](ADR-44-shadcn-copy-paste.md) | shadcn/ui (copy-paste) over UI library | Accepted |
| [45](ADR-45-react-query-zustand.md) | React Query for server state + Zustand for UI state | Accepted |
| [46](ADR-46-openapi-typescript-types-only.md) | `openapi-typescript` types only (no client codegen) | Accepted |
| [47](ADR-47-rhf-zod-mirrored-schemas.md) | Forms: React Hook Form + Zod mirroring backend constraints | Accepted |
| [48](ADR-48-playwright-day-one.md) | Playwright for e2e from day one | Accepted |
| [50](ADR-50-proxy-to-backend-helper.md) | `proxyToBackend` helper for Route Handlers | Accepted |
| [51](ADR-51-ssr-pre-fill-me.md) | SSR pre-fill for `useMe` (no flash) | Accepted |
| [52](ADR-52-api-fetch-split.md) | `apiFetch` vs `authedFetch` split | Accepted |
| [53](ADR-53-optimistic-favorites.md) | Optimistic favorite mutations (snapshot + rollback) | Accepted |
| [54](ADR-54-media-tile-placeholder.md) | `MediaTilePlaceholder` for missing GET `/media/{id}` | Accepted |
| [55](ADR-55-upload-modal-ux.md) | Modal upload UX with post-publish redirect | Accepted |

### Deferred + debt

| # | Title | Status |
|---|---|---|
| [56](ADR-56-defer-media-by-id.md) | Defer GET `/api/v1/media/{id}` | Deferred |
| [57](ADR-57-defer-testcontainers.md) | Defer Testcontainers integration tests | Deferred (debt) |
| [58](ADR-58-targeted-e2e.md) | Targeted Playwright coverage (no exhaustive matrix) | Accepted |
| [59](ADR-59-defer-mobile.md) | Defer iOS / Android clients | Deferred |

### Bug post-mortems

Each surfaced a durable learning worth recording. Numbered B-prefix to keep
chronology of decisions vs reactions visible at a glance.

| # | Title | Slice | Tags |
|---|---|---|---|
| [60](ADR-60-bug-v4-column-already-exists.md) | V4 migration tried to add existing column | Backend 3 | data, migration |
| [61](ADR-61-bug-citext-hibernate.md) | `citext` rejected by Hibernate validator | Backend 3 | data, ORM |
| [62](ADR-62-bug-double-registered-filter.md) | `@Component Filter` double-registered → POST 403 | Backend 3 | security |
| [63](ADR-63-bug-inet-hibernate.md) | `inet` rejected by Hibernate validator | Backend 3 | data, ORM |
| [64](ADR-64-bug-error-dispatch-hijack.md) | `/error` dispatch intercepted by auth entry point | Backend 3 | security |
| [65](ADR-65-bug-camelcase-snake-mismatch.md) | TS DTOs typed snake_case from `@Schema` vs camelCase wire | Web 1 | contract |
| [66](ADR-66-bug-isr-cached-empty.md) | ISR cached empty initial during bug window | Web 1 | rendering |
| [67](ADR-67-bug-cache-serializer-erasure.md) | Cache serializer lost generic type info | Backend 1 | cache |
| [68](ADR-68-bug-img-zero-dimensions.md) | `<img loading=lazy>` + `h-auto` rendered 0×0 in Playwright | Web 1 | layout |
| [69](ADR-69-bug-curly-quote-regex.md) | Curly-quote heading vs ASCII-quote test regex | Web 1 | tests |
| [70](ADR-70-bug-s3-key-not-null.md) | `UploadService` violated `s3_key NOT NULL` | Backend 2 | data |
| [71](ADR-71-bug-dispatch-race.md) | Transcode dispatch race (publish before tx commit) | Backend 2 | concurrency |
| [72](ADR-72-bug-libwebp-missing.md) | Homebrew FFmpeg missing `libwebp` encoder | Backend 2 | ops |
| [73](ADR-73-bug-minio-bucket-private.md) | MinIO bucket private by default → renditions 403 | Backend 2 | ops, storage |
| [74](ADR-74-bug-api-docs-yaml-401.md) | `/v3/api-docs.yaml` not in `permitAll` list | Backend 3 | security |

## How to use

- **Joining the team?** Read ADR-00 through ADR-10 to orient on the foundation,
  then dip into the ones tagged for whatever module you're starting in.
- **Reviewing a design?** Check whether the change conflicts with any existing
  ADR, or proposes superseding one.
- **Adding a new one?** Take the next number, set status, link from this index.
- **Bug fix worth remembering?** Add a `B`-tagged ADR; keep numbering flat.
