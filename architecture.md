# FlashGif — Architecture

A reference description of the FlashGif backend as it stands today. Companion to [CLAUDE.md](CLAUDE.md) (orientation) and [progress.md](progress.md) (build log with decision history). This document is static — it describes *what is*, not what was built when.

---

## 1. What it does

FlashGif is a Giphy-style platform for searching, uploading, and sharing GIFs and short videos. It supports:

- Public, anonymous search and discovery
- End-user accounts (email + password) with favorites, custom collections, and public creator channels
- Direct-to-S3 uploads with server-side FFmpeg transcoding to multiple renditions
- A third-party developer API with hashed API keys, per-key rate limiting, and usage analytics

The backend is the only artifact in scope today. Frontend (Next.js) and mobile (Swift + Kotlin) are downstream consumers of the OpenAPI contract this backend publishes.

---

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Runtime | Java 21 (Temurin) | Latest LTS; pattern matching, records, virtual threads available if needed |
| Framework | Spring Boot 3.3.5 | Mature, batteries-included; aligned with team familiarity |
| Build | Gradle 8.10.2 (Groovy DSL) | Wrapper-pinned for reproducibility |
| Persistence | Spring Data JPA + Hibernate 6 | Standard ORM for relational data |
| Migrations | Flyway | Versioned SQL; production-grade |
| Primary DB | PostgreSQL 16 | Truth store; JSONB for flexible fields |
| Search | Elasticsearch 8.15 | Lexical search, autocomplete, popularity-weighted ranking |
| Cache + counters | Redis 7 | Trending cache, suggestion cache, developer usage counters |
| Messaging | RabbitMQ 3.13 | Transcode job queue with DLQ |
| Object storage | S3 (MinIO locally) | Originals + transcoded renditions |
| Media processing | FFmpeg 8 (host binary, `ProcessBuilder`) | mp4, animated webp, gif, poster jpeg |
| Auth | Spring Security 6 + JJWT 0.12.6 + BCrypt | JWT access + opaque refresh; API keys for developers |
| Rate limiting | Hand-rolled in-memory token bucket | Single-instance correct; swap to Bucket4j-Redis when distributed |
| API docs | SpringDoc OpenAPI 2.6 | `/swagger-ui.html` + machine-readable `/v3/api-docs.yaml` |

---

## 3. System topology

```
                       ┌─────────────────────────────────────────────────────┐
                       │                                                     │
   anonymous users ───►┤                                                     │
   logged-in users ───►┤        Spring Boot (modular monolith)               │
                       │                                                     │
   third-party apps ──►┤  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
   (Authorization:    │  │ user JWT │  │ developer│  │ public    │           │
    Bearer <key>)     │  │  chain   │  │  API-key │  │  endpoints│           │
                       │  └──────────┘  │  chain   │  └──────────┘           │
                       │                └──────────┘                         │
                       │                                                     │
                       └───┬───────────┬───────────┬───────────┬─────────────┘
                           │           │           │           │
                           ▼           ▼           ▼           ▼
                     ┌──────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐
                     │ Postgres │ │  ES     │ │ Redis  │ │ RabbitMQ │
                     │ (truth)  │ │ (search │ │(cache, │ │ (jobs)   │
                     │          │ │  index) │ │ counter│ │          │
                     │          │ │         │ │ , rate)│ │          │
                     └──────────┘ └─────────┘ └────────┘ └──────────┘
                                                              │
                                                              ▼
                                                       ┌─────────────┐
                                                       │ FFmpeg      │
                                                       │ worker      │
                                                       │ (@RabbitLis-│
                                                       │  tener,     │
                                                       │  same JAR)  │
                                                       └─────────────┘
                                                              │
                                                              ▼
                                                       ┌──────────────┐
                                                       │ S3 / MinIO   │
                                                       │  uploads/    │
                                                       │  renditions/ │
                                                       └──────────────┘
```

**Shape choice:** modular monolith, not microservices. Single deployable JAR with strict package-by-feature boundaries. Faster iteration, simpler ops, no distributed-transactions tax. Extracting any module to a separate process later is a refactor, not a rewrite.

---

## 4. Backend module map

```
com.flashgif.
├── search/        — keyword search, trending, autocomplete
│   ├── api/       — REST controllers + DTOs
│   ├── domain/    — services (SearchService, TrendingService, SuggestionService)
│   ├── index/     — ES document, mapping JSON, IndexInitializer
│   └── sync/      — OutboxPoller, MediaIndexer
│
├── media/         — upload orchestration + transcode pipeline
│   ├── api/       — UploadController, MetadataController, DTOs (incl. MediaSummary)
│   ├── domain/    — Media, MediaUpload, UploadService, PublishService, enums
│   ├── storage/   — S3 client config, presigned URL helpers, bucket bootstrap
│   ├── transcode/ — RabbitMQ config, FFmpegRunner, TranscodeWorker
│   ├── popularity/— PopularityRecomputeJob (5-min scheduled)
│   └── dev/       — MediaSeeder (local profile only)
│
├── users/         — accounts, JWT, refresh tokens
│   ├── api/       — AuthController, UserController, DTOs
│   ├── domain/    — User, RefreshToken, UserService, AuthService
│   ├── security/  — JwtService, JwtProperties, UserPrincipal, UserJwtFilter
│   └── dev/       — UserSeeder (local profile only)
│
├── favorites/     — flat favorites + curated collections
│   ├── api/       — FavoritesController, CollectionsController, PagedResponse<T>
│   └── domain/    — Favorite + FavoriteId, MediaCollection, CollectionItem,
│                    FavoritesService, CollectionsService
│
├── channels/      — public creator profile reads + own-profile updates
│   ├── api/       — ChannelsController, ChannelResponse, UpdateProfileRequest
│   └── domain/    — ChannelsService (reads users + media; no entities of its own)
│
├── developer/     — API keys, dev-facing search proxy, usage analytics
│   ├── api/       — DeveloperKeysController, DeveloperSearchController,
│   │               UsageAnalyticsController, DTOs
│   ├── domain/    — DeveloperKey, DeveloperKeyService, UsageRecorder
│   ├── ratelimit/ — TokenBucketLimiter, RateLimitProperties
│   └── security/  — DeveloperPrincipal, DeveloperApiKeyFilter, DeveloperRateLimitFilter
│
└── infra/         — cross-cutting wiring (no feature logic)
    ├── security/  — SecurityConfig (two filter chains)
    ├── openapi/   — OpenApiConfig (SpringDoc bean + bearer-jwt scheme)
    ├── outbox/    — OutboxEvent, OutboxRepository, OutboxPublisher façade
    └── cache/     — CacheConfig (RedisCacheManager, per-cache TTLs)
```

**Dependency rules (enforced by code review today, ArchUnit later):**
- Feature modules may depend on `infra`.
- `infra` may depend on nothing else (no feature modules).
- Feature modules generally don't depend on each other, with two pragmatic exceptions:
  - `search/sync/MediaIndexer` reads `media.domain.Media` and `users.domain.User` to project the search document.
  - `channels/domain/ChannelsService` reads `media.domain.MediaRepository` and uses `media.api.dto.MediaSummary`.
  Both are documented in their Javadoc; both would become event contracts if the modules ever split.

---

## 5. Key data flows

### 5.1 Search request

```
GET /api/v1/search?q=happy&type=gif
  │
  ▼
SecurityConfig.userChain  ─── permitAll for GET /search/** ──► no auth needed
  │
  ▼
SearchController
  │
  ▼
SearchService.search()
  ├── builds bool { must: multi_match(q, fuzziness=AUTO), filter: status=published [+ type] }
  ├── wraps with function_score (popularity field_value_factor, log1p) when sort=relevance
  ├── executes via ElasticsearchOperations.search(NativeQuery, MediaDocument.class)
  ├── MediaProjector: MediaDocument → MediaSummary
  └── returns SearchResponse { items, page, size, total, tookMs }
```

Empty `q` falls through to `TrendingService.asSearchResponse()` — same DTO shape, different data source.

### 5.2 Trending (cached)

```
GET /api/v1/trending
  ├── TrendingService.top(type) is @Cacheable("trending", key=type)
  ├── Cache hit (60s TTL): return from Redis
  └── Cache miss: ES query (filter: status=published [+ type], sort: popularity desc, created_at desc)
                  → cache + return
```

### 5.3 Media upload + transcode pipeline

```
Browser                Backend                  Postgres   S3/MinIO   RabbitMQ   FFmpeg worker
  │                       │                        │          │           │             │
  │ POST /upload          │                        │          │           │             │
  │   {filename,type,size}│                        │          │           │             │
  │ ────────────────────► │                        │          │           │             │
  │                       │ INSERT media_uploads   │          │           │             │
  │                       │ (status=AWAITING)      │          │           │             │
  │                       │ stamp uploader_id      │          │           │             │
  │                       │ from UserPrincipal     │          │           │             │
  │                       │ ──────────────────────►│          │           │             │
  │                       │ presign PUT URL ──────────────────►│          │           │             │
  │ ◄──────────────────── │ {uploadId, presignedUrl, expiresAt}                        │             │
  │                       │                        │          │           │             │
  │ PUT file ─────────────────────────────────────────────────►│          │           │             │
  │ ◄──── 200                                                  │          │           │             │
  │                       │                        │          │           │             │
  │ POST /complete        │                        │          │           │             │
  │ ────────────────────► │ HEAD s3://... ──────────────────►│          │           │             │
  │                       │ UPDATE status=UPLOADED │          │           │             │
  │                       │ publish ──────────────────────────────────────►media.transcode             │
  │ ◄──── 202             │                        │          │           │             │
  │                                                                       │             │
  │                                                          consume ────►│             │
  │                                                                                     │
  │                                              UPDATE status=PROCESSING │             │
  │                                                                                     │
  │                                                       download original ◄───────────│
  │                                                       ffprobe → w/h/duration         │
  │                                                       transcode 4 renditions         │
  │                                                       upload renditions ─────────────│ ──► S3
  │                                                                                     │
  │                                              UPDATE status=READY,                   │
  │                                                     rendition_urls={mp4,webp,gif,poster}
  │                                                                                     │
  │ POST /metadata        │                        │          │           │             │
  │   {uploadId,title,    │                        │          │           │             │
  │    tags,rating}       │                        │          │           │             │
  │ ────────────────────► │ INSERT media (carry rendition_urls + uploader_id)            │
  │                       │ UPDATE upload status=PUBLISHED                               │
  │                       │ INSERT outbox_events ('media.published') ── same tx          │
  │ ◄──── 201             │                                                              │
  │                       │                                                              │
  │                       │   ~2s later: OutboxPoller → MediaIndexer → ES                │
  │                       │                                                              │
  │ now searchable        │                                                              │
```

**Key design choices:**
- **Browser uploads directly to S3** via presigned PUT; backend never sees the bytes.
- **Two tables** — `media_uploads` (state machine) and `media` (published). Pipeline failures never pollute the searchable corpus.
- **State machine** with explicit transitions on the entity: `AWAITING_UPLOAD → UPLOADED → PROCESSING → READY → PUBLISHED`, with `FAILED` as the terminal error state. Illegal transitions throw at the entity, not silently.
- **Worker = same JAR, profile-gated.** Default profile runs API + worker together (simplest dev). Production deploys can use `--spring.profiles.active=worker` to specialize.

### 5.4 Outbox → Elasticsearch (the bridge)

Every domain change that should be reflected in search writes a row to `outbox_events` *in the same transaction* as the domain change. A scheduled poller drains the outbox to Elasticsearch. This solves the dual-write problem (where two systems can disagree if one write succeeds and the other fails) without needing 2PC.

```
@Transactional
publish(media):
   mediaRepository.save(media)             ─┐
   outboxRepository.save(                   │  one DB transaction
     OutboxEvent.of("media", media.id,      │  all-or-nothing
                    "media.published",      │
                    {mediaId: media.id}))  ─┘

(every ~2s)
OutboxPoller.drain():
   findUnpublished(limit=100)
   for each event:
       MediaIndexer.upsert(event.mediaId)   # reloads from Postgres, projects, ES.save
       event.publishedAt = now              # idempotent: re-runs just re-upsert

ES upserts are idempotent (key=mediaId), so at-least-once delivery is safe.
```

The outbox is also used for **popularity-driven reindex**. The `PopularityRecomputeJob` runs every 5 minutes, recomputes `popularity = log(1 + favorite_count*3 + view_count) * exp(-age_days/7)` for recently-changed media, and writes an outbox event for any meaningful change. This decouples per-favorite write amplification from search-index updates.

### 5.5 Authentication — two parallel models

**User chain — JWT (stateless)**

```
POST /api/v1/auth/login {email, password}
  │
  ▼
AuthService.login()
  ├── lookup by normalized email (UserService normalizes to lowercase)
  ├── BCrypt.matches(rawPassword, stored hash)
  ├── JwtService.issue(userId, email) → HS256-signed JWT, 15-min TTL
  │   claims: { sub=userId, email, iat, exp, jti }
  └── new RefreshToken (raw = 256-bit random; SHA-256 hash stored; 30-day TTL)
      returns AuthResponse { accessToken, expiresInSeconds, refreshToken }

GET /api/v1/users/me
  Authorization: Bearer <JWT>
  │
  ▼
UserJwtFilter (OncePerRequestFilter, before AuthorizationFilter)
  ├── parse + verify JWT signature, issuer, exp
  ├── set SecurityContext with UserPrincipal(userId, email)
  └── soft-fail on invalid → clear context, let permitAll endpoints still flow
```

**Refresh token rotation:** every `/auth/refresh` revokes the presented refresh token (sets `revoked_at`, points `replaced_by` at the new id) and issues a fresh pair. Refresh tokens are *opaque* (not JWTs) so revocation is trivial (delete/update the DB row); the short JWT TTL bounds the revocation lag for access tokens.

**Developer chain — API keys**

```
GET /api/v1/developer/trending
  Authorization: Bearer <api-key>     ("fg_..." prefix)
  │
  ▼
DeveloperApiKeyFilter
  ├── SHA-256 hash the presented key
  ├── lookup developer_keys by hash; check status=active
  └── set SecurityContext with DeveloperPrincipal(keyId, ownerId)
  │
  ▼
DeveloperRateLimitFilter
  ├── TokenBucketLimiter.tryAcquire(keyId)
  │     in-memory ConcurrentHashMap<UUID, Bucket>
  │     refill rate: requestsPerMinute/60 per second; burst: requestsPerMinute
  ├── on deny: 429 + Retry-After header (no usage recorded)
  └── on allow: UsageRecorder.record(keyId)
                  ├── INCR Redis "dev:usage:{keyId}:{yyyyMMdd}" (35-day TTL)
                  └── debounced UPDATE developer_keys.last_used_at (max once per 60s/key)
  │
  ▼
DeveloperSearchController → delegates to existing SearchService etc.
```

**Two `SecurityFilterChain` beans, ordered:**
1. `@Order(1)` `developerChain` — `securityMatcher("/api/v1/developer/**")`, requires API key
2. `@Order(2)` `userChain` — catch-all; permitAll list + JWT-or-anonymous for the rest

Key management endpoints (`POST/GET/DELETE /api/v1/auth/developer/keys`) live in the user chain — issuing/revoking is a user operation, not a key operation.

---

## 6. Data model

Authoritative DDL lives in `backend/src/main/resources/db/migration/V*.sql`. Logical overview:

| Table | Purpose | Owned by |
|---|---|---|
| `users` | Accounts, public-channel profile fields, verification | `users/` + `channels/` |
| `refresh_tokens` | Rotating opaque refresh credentials (SHA-256 hashed) | `users/` |
| `media` | Published, searchable media (source of truth) | `media/` |
| `media_tags` | Tags per media (m:n, value type via `@ElementCollection`) | `media/` |
| `media_uploads` | Upload pipeline state machine (separate from `media`) | `media/` |
| `outbox_events` | Transactional outbox bridging Postgres → Elasticsearch | `infra/outbox/` |
| `favorites` | Per-user flat favorites list (composite PK) | `favorites/` |
| `media_collections` | Owned named folders (public or private) | `favorites/` |
| `collection_items` | Media inside collections (composite PK) | `favorites/` |
| `developer_keys` | Third-party API keys (SHA-256 hashed) | `developer/` |

Elasticsearch holds one index, `media_v1` (aliased as `media`), populated from the outbox. The mapping is authoritative in `resources/elasticsearch/media-mapping.json`; the `MediaDocument` class annotations are informational only (`createIndex = false`).

Redis holds:
- `trending:*` — cached trending lists (60s TTL)
- `suggestions:*` — cached autocomplete results (5m TTL)
- `dev:usage:{keyId}:{yyyyMMdd}` — daily request counters (35-day TTL)

---

## 7. Cross-cutting patterns

These patterns appear in multiple modules and are worth knowing once:

### Transactional outbox
Domain change + outbox insert in one transaction; a scheduled poller drains the outbox to a secondary system. Currently used for media → Elasticsearch. Single-consumer for v1; if we add a second consumer (analytics, notifications) we'll route through RabbitMQ.

### Hash-on-write for secrets
`refresh_tokens.token_hash` and `developer_keys.key_hash` both store SHA-256 of the raw token. The raw is shown to the client exactly once at issuance. Revocation is a `revoked_at` timestamp; lookup is a hash comparison.

### Pessimistic locking for counters
`media.favorite_count` is mutated via `MediaRepository.findByIdForUpdate(id)` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) before increment/decrement. Prevents lost updates under concurrent favoriting.

### Two `SecurityFilterChain` beans
Distinct auth models (user JWT vs developer API key) get distinct chains via `@Order` + `securityMatcher`. Each chain has its own filters and rate-limit policy.

### 404 not 403 for ownership failures
Endpoints that check ownership return 404 (not 403) when the caller isn't the owner of a private resource. Don't leak the existence of private collections / keys / etc.

### `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`
First rule in the user chain. Without it, Spring's internal `/error` dispatch gets intercepted by our `AuthenticationEntryPoint` and masks all non-200 statuses as empty 401s. Lesson learned the hard way during Slice 3.

### Filter auto-registration suppression
`@Component Filter` beans get *also* registered as top-level servlet filters by Spring Boot, in addition to being added to security chains via `addFilterBefore`. The result is double-execution and confusing ordering interactions. Every custom filter has a matching `FilterRegistrationBean<T>` bean with `setEnabled(false)` to suppress the auto-registration.

### Vendor types vs Hibernate validation
Postgres-specific types (`citext`, `inet`) trip Hibernate's strict schema validator because the JDBC type code doesn't match the default String → VARCHAR mapping. Pattern: prefer plain `varchar(N)` columns and handle case-insensitivity / IP-formatting at the app layer. Documented in V5 (`citext` → `varchar`) and V6 (`inet` → `varchar`).

---

## 8. Storage layout (S3 / MinIO)

```
flashgif-media/
├── uploads/{uploadId}/{filename}                # original, kept for re-transcode
└── renditions/{uploadId}/{mp4,webp,gif,poster}.{ext}
```

Bucket is created idempotently by `StorageConfig.BucketBootstrapper` on startup. Dev CORS policy is permissive (`*` origins, `PUT/GET/HEAD`); production environments should tighten this per-environment.

---

## 9. Local development runtime

```
ops/docker-compose.yml brings up:
├── postgres:16.4-alpine            → :5432
├── elasticsearch:8.15.3            → :9200
├── redis:7.4-alpine                → :6379
├── rabbitmq:3.13-management-alpine → :5672, :15672 (management UI)
└── minio                           → :9000 (S3 API), :9001 (console)
```

Spring Boot connects to all five via the defaults in [application.yml](backend/src/main/resources/application.yml) (env-var overridable). Detailed runbook: [ops/instructions.md](ops/instructions.md).

DevTools is on the classpath; auto-restart fires when IDE classes change (see [CLAUDE.md](CLAUDE.md) for the IntelliJ auto-build settings).

---

## 10. Spring profiles

- **default** — production-shape. API + worker in one process. Real env vars expected.
- **local** — dev shape. Activates `MediaSeeder` (10 sample media) and `UserSeeder` (dev user `dev@flashgif.example` / `dev-password`). Enables security DEBUG logging. Auto-activated when nothing is set.
- **worker** — worker-only. Disables the HTTP server (`server.port: -1`). For production split where Rabbit consumers run on separate boxes from API serving.

---

## 11. Deployment topology (conceptual)

The local docker-compose stack is **not** production-shaped. For production:

| Local | Production equivalent |
|---|---|
| Postgres in compose | Managed Postgres (RDS, Cloud SQL, etc.) with read replicas |
| Elasticsearch single-node | Managed Elastic Cloud / OpenSearch with 3+ nodes |
| Redis in compose | ElastiCache / Memorystore with persistence + failover |
| RabbitMQ in compose | Amazon MQ / CloudAMQP cluster |
| MinIO | Real S3 with CloudFront/Cloudflare CDN in front of `renditions/` |
| FFmpeg on host PATH | FFmpeg in the worker Docker image (`ops/ffmpeg/Dockerfile` — not built yet) |
| Single JAR running API + worker | Two deployments: API (`--spring.profiles.active=default` minus listeners) + Worker (`--spring.profiles.active=worker`), autoscaled independently |
| Hand-rolled in-memory rate limiter | Bucket4j-Redis (swap is single-class) |
| Redis-only usage counters with TTL | Counters + nightly rollup job to `developer_usage_daily` for long retention |

The application code itself doesn't change for any of these; only the connection strings, deployment manifests, and one rate-limiter implementation file.

---

## 12. API surface (summary)

Full spec: `GET /v3/api-docs.yaml` (or `./gradlew exportOpenApi` to snapshot it to `docs/openapi.yaml`).

### Public (no auth)
- `GET /api/v1/search` — keyword search
- `GET /api/v1/trending`
- `GET /api/v1/search/suggestions`
- `GET /api/v1/channels/{username}` — public profile + top media
- `GET /api/v1/channels/{username}/media` — paged uploads
- `GET /api/v1/users/{username}/collections` — public collections only
- `GET /api/v1/collections/{id}` — public collection details
- `GET /api/v1/collections/{id}/items`

### Auth (no token, returns one)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### User-authenticated (Bearer JWT)
- `GET    /api/v1/users/me`
- `PATCH  /api/v1/users/me`
- `PATCH  /api/v1/channels/profile`
- `POST   /api/v1/media/upload`
- `POST   /api/v1/media/upload/{uploadId}/complete`
- `GET    /api/v1/media/status/{uploadId}`
- `POST   /api/v1/media/metadata`
- `POST   /api/v1/favorites`
- `DELETE /api/v1/favorites/{mediaId}`
- `GET    /api/v1/users/me/favorites`
- `POST   /api/v1/collections`
- `GET    /api/v1/users/me/collections`
- `PATCH  /api/v1/collections/{id}`
- `DELETE /api/v1/collections/{id}`
- `POST   /api/v1/collections/{id}/items`
- `DELETE /api/v1/collections/{id}/items/{mediaId}`
- `POST   /api/v1/auth/developer/keys`
- `GET    /api/v1/auth/developer/keys`
- `DELETE /api/v1/auth/developer/keys/{id}`
- `GET    /api/v1/usage/analytics`

### Developer API (Bearer API key, rate-limited)
- `GET /api/v1/developer/search`
- `GET /api/v1/developer/trending`
- `GET /api/v1/developer/search/suggestions`

---

## 13. What's deliberately not built

This is the canonical list of things we chose to defer; each was a documented call, not an oversight:

**Search**
- Synonyms, semantic / vector ranking, personalisation
- Deep pagination via `search_after` (capped at page 50 today)

**Media**
- Resumable / multipart uploads
- Multi-resolution renditions per format (single resolution per format today)
- Background sweeper for abandoned `AWAITING_UPLOAD` rows
- S3 event-based completion (use explicit `/complete` instead)
- Per-user upload quotas

**Auth**
- Email verification flow (assumed verified on register)
- Password reset (no email service plumbed)
- OAuth providers (Google, Apple, etc.)
- 2FA, account deletion, GDPR export
- Active-session management UI

**Favorites / collections**
- Reordering items within a collection (`position` column exists, no API)
- Public collection share URLs / OG metadata
- Collection cover images
- Collaborative collections (single owner only)

**Channels**
- Avatar / banner upload flow (URL fields only today)
- Verified-badge admin endpoint (column exists, no API)
- Follow / following
- Channel analytics

**Developer**
- Distributed (multi-instance) rate limiting → Bucket4j-Redis swap
- Per-endpoint analytics breakdown
- Latency / error-rate metrics
- Scopes (read-only / search-only / full)
- IP allowlist per key, webhooks
- Long-term usage archive (`developer_usage_daily` rollup)
- Self-service quotas / billing

**Cross-cutting**
- Testcontainers-backed integration tests (would have caught the V4 / `citext` / `inet` bugs at build time)
- ArchUnit module-boundary enforcement
- Admin role / endpoints (verified badge, key revocation, etc. all need this eventually)
