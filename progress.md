# Build Progress Log

A running journal of every scaffolding step and every decision. Newest entries at the bottom of each section.

## Conventions for this log
- **Step** — what was done, in past tense
- **Decision** — a choice made and the *why*
- **Open question** — something I deferred or want to revisit

---

## 2026-05-20 — Session 1: backend scaffolding

### Environment snapshot
- Java: Temurin 21.0.11 (LTS) ✅ matches stack
- Gradle: 9.5.1 (system install) ✅
- Docker: 29.4.3 ✅
- Working directory: `/Users/bhavani/Developer/projects/giphyc`
- Initial state: only `prd.txt` and `CLAUDE.md` present

### Decisions
- **Build tool: Gradle (Groovy DSL).** Most Spring Boot tutorials/examples are Groovy DSL; lower friction than Kotlin DSL for a Java-only backend. Re-evaluate if/when we add Kotlin code.
- **Single Gradle project, not multi-module.** We're a modular monolith using *package* boundaries, not Gradle subprojects. Module isolation is enforced by code review + (later) ArchUnit tests, not by Gradle. Easier builds, faster CI, simpler IDE import. If a module ever needs to ship independently, we split at that point.
- **Spring profiles, not separate JARs, for API vs Worker.** Default profile runs both API listeners and Rabbit consumers (simplest local dev). Production deploys can use `--spring.profiles.active=api` or `worker` to specialize. Avoids second build artifact.
- **Postgres 16, Elasticsearch 8.x, Redis 7, RabbitMQ 3 mgmt, MinIO** — current LTS/stable lines. Pinning specific patch tags in compose to avoid drift.
- **Local infra via docker-compose, not Testcontainers-only.** Compose for `bootRun` and ad-hoc dev; Testcontainers for the test classpath. Two different jobs.
- **Flyway baseline starts empty.** First real migration (`V2__media.sql`) lands when we implement the Search slice. Keeps history clean.

### Open questions
- ArchUnit tests for module boundaries — defer until we have >1 module with real code.
- OpenAPI client codegen for web/mobile — defer; first generate `docs/openapi.yaml` via SpringDoc and confirm output shape.

### Steps
- Created `progress.md` (this file).
- Created `.gitignore` covering Gradle/Java, Node/Next.js, Android, iOS, IDEs, env files, and `ops/data/` (compose volume mounts).
- Created `ops/docker-compose.yml` with Postgres 16.4, Elasticsearch 8.15, Redis 7.4, RabbitMQ 3.13-mgmt, MinIO. All services have healthchecks; volumes mount under `ops/data/` (gitignored).
  - **Decision:** RabbitMQ creds = `flashgif/flashgif`, MinIO creds = `flashgif/flashgif-secret`. Dev only; real envs use secrets.
  - **Decision:** MinIO console exposed at 9001 — useful for verifying bucket contents during upload feature work.
  - **Decision:** No bucket-init container yet — we'll add a Spring `@Bean` that creates the bucket on startup (more idempotent than a sidecar).
- Created `backend/settings.gradle` (`rootProject.name = 'flashgif-backend'`) and `backend/build.gradle`.
  - **Decision:** **Spring Boot 3.3.5** + **Java 21 toolchain**. 3.3.x is current stable; 3.4 is fresh and we have no reason to chase it.
  - **Decision:** **AWS SDK v2** for S3 (works against MinIO with `path-style: true`). No `spring-cloud-aws` — too much auto-config we don't need.
  - **Decision:** **JJWT 0.12.6** for JWTs (the modern API; supersedes the deprecated `jjwt:jjwt`).
  - **Decision:** **Lombok in** — Spring/Java boilerplate (getters, builders) gets noisy fast. Used only for trivial cases (`@Getter`, `@Builder`, `@RequiredArgsConstructor`), never for `@Data` on entities.
  - **Decision:** Testcontainers wired in test scope (postgres/elasticsearch/rabbitmq), but no `@SpringBootTest` yet — we'll add one per feature slice to keep test runtime reasonable.
  - **Decision:** Added a manual `exportOpenApi` Gradle task that pulls `/v3/api-docs.yaml` from a running app and writes `docs/openapi.yaml`. Cleaner than a build-time agent for now; we can swap to `springdoc-openapi-gradle-plugin` later if we want CI-time generation.
- Generated Gradle wrapper at **8.10.2** (Spring Boot 3.3.5's tested wrapper). System Gradle is 9.5.1 but the wrapper pins everyone to the same version.
- **First build failed** on `com.bucket4j:bucket4j-core:8.14.0` — version doesn't exist on Maven Central with that exact coord. Rather than hunt the right artifact name, I removed bucket4j from the skeleton. **Decision:** Add rate limiting when we implement the Developer API slice (Feature 4), since that's the only consumer. Avoids carrying unused deps.
- Created `FlashgifApplication.java` with `@EnableScheduling` (needed for the outbox poller) and `@EnableAsync` (worker-style fire-and-forget tasks).
- Created `application.yml` with sane defaults + env-var overrides for every external endpoint (DB, ES, Redis, RabbitMQ, S3). Profiles: `local` (default), `worker`.
  - **Decision:** `spring.jpa.hibernate.ddl-auto: validate` — Flyway is the only thing that touches schema. `open-in-view: false` to keep transaction boundaries explicit.
- Created **seven package-by-feature roots** under `com.flashgif`: `search`, `media`, `users`, `favorites`, `channels`, `developer`, `infra`. Each has a `package-info.java` declaring its responsibility and dependency rules.
  - **Decision:** Use `package-info.java` Javadoc as the human contract; **defer ArchUnit** until at least two modules have real code.
- Created `OpenApiConfig` (SpringDoc bean wiring API title/version/servers). Swagger UI at `/swagger-ui.html`, JSON at `/v3/api-docs`, YAML at `/v3/api-docs.yaml`.
- Created `SecurityConfig` with a single `SecurityFilterChain` (stateless, CSRF off, all endpoints permitted) plus a TODO marker. Two-chain split (user JWT vs developer API key) lands with the Users slice.
- Created `db/migration/V1__baseline.sql` — intentionally empty placeholder so Flyway has a clean history root.
- Created `FlashgifApplicationTests` — lightweight sanity test (no `ApplicationContext` start) so `./gradlew build` actually runs `test` task.
- **Smoke build green:** `./gradlew build` — BUILD SUCCESSFUL in 16s (compile + jar + test).

- Created `ops/instructions.md` — self-contained runbook for the ops team. Covers prerequisites, ports, credentials, start/stop/wipe commands, per-service health checks, backup, env-var overrides for remote hosts, and an explicit **"not suitable for production"** section pointing to managed equivalents (RDS, Elastic Cloud, ElastiCache, Amazon MQ, S3).
  - **Decision:** Document in `ops/` rather than the repo root so it travels with the compose file. Repo root stays focused on `CLAUDE.md` / `progress.md` / `prd.txt`.
  - **Decision:** Include a "Production notes" section even though the ask was only for run instructions. Better to tell the ops team upfront what *won't* fly in prod than have them discover it during a deploy.

- **First `docker compose up -d` run.** All five services reached `(healthy)` in ~13s after image pull (~1m for the Elasticsearch image on first pull).
- Verified each service end-to-end:
  - Postgres 16.4 — `SELECT version()` returns
  - Elasticsearch — cluster `status: green`, 1 node, 0 shards (expected for fresh cluster)
  - Redis — `PING → PONG`
  - RabbitMQ — `rabbitmq-diagnostics ping → succeeded`
  - MinIO — `/minio/health/live → 200`
- **No config issues surfaced** — ports, volumes, healthchecks, and image tags all resolve cleanly.
- **First Spring Boot app boot (run from user's IDE).** App came up on `:8080`. `/actuator` returned the expected HAL index with `health`, `info`, `metrics` links — matches our `management.endpoints.web.exposure.include` config. Implicit confirmation that Flyway baseline ran, JPA validated against an empty schema, and Spring Data ES/Redis/RabbitMQ auto-config all connected (otherwise the context would have failed at boot).

---

## 2026-05-20 — Session 2: Search & Discovery slice

### Decisions
- **Outbox event payload = `{"mediaId": "<uuid>"}` only.** Indexer reloads from Postgres on dequeue. Always-fresh state; smaller events; tradeoff is one extra DB hit per event (negligible at expected volume; can change later).
- **`search` depends on `media.domain` types directly.** Acceptable cross-module dep for v1; documented in `MediaIndexer` Javadoc. If `search` were ever extracted to its own process, it would consume a media REST/event contract instead. ArchUnit can enforce the inverse direction later.
- **No RabbitMQ on indexing path (yet).** Outbox poller writes ES directly. Once a second consumer exists (analytics, notifications), we'll publish to RabbitMQ and let consumers fan out.
- **Per-cache TTL via `RedisCacheManager`.** `trending` = 60s (high traffic / low cardinality); `suggestions` = 5m (broader prefix space). Configured in `infra/cache/CacheConfig`.
- **`search_as_you_type` on `title.suggest`** (not the `completion` suggester) — built-in n-gram subfields, no analyzer config, single `multi_match` query covers it. Easier to evolve.
- **Index bootstrap: alias `media` → `media_v1`.** `IndexInitializer` is an `ApplicationRunner`; idempotent. Future schema changes will build `media_v2` and atomic-swap the alias — no client-side change.
- **MediaSeeder is `@Profile("local")` only.** Idempotent (skips if `media.count() > 0`). Inserts 10 rows + outbox events so the search slice works end-to-end before the upload slice exists. Uses `example.invalid` URLs — real renditions land with Feature 2.
- **Compile errors caught early:**
  - `NativeQueryBuilder` is a top-level class in `…elc`, not a nested type — fixed import.
  - `Media`'s `@NoArgsConstructor` was `PROTECTED` (a "force-use-factory" pattern). No factory exists yet and the seeder needs to construct, so dropped to public. Will revisit when a real Media factory/service lands with the upload slice.

### Steps
- Added `V2__media_and_outbox.sql` — `media` (with type/rating/status CHECKs), `media_tags` (PK on (media_id,tag)), `outbox_events`. Partial indexes on `popularity` (filtered `WHERE status='published'`) and on unpublished outbox rows.
- Added enums: `MediaType`, `ContentRating`, `MediaStatus` with `dbValue()` / `fromDb()` helpers. Entity stores the lowercase string (keeps the DB CHECK authoritative).
- Added `Media` JPA entity with `@JdbcTypeCode(SqlTypes.JSON)` for `rendition_urls` (Hibernate 6 JSONB mapping) and `@ElementCollection` for tags.
- Added `OutboxEvent` entity, `OutboxRepository` with `findUnpublished(Pageable)`, and `OutboxPublisher` façade that takes any POJO + serializes via Jackson.
- Added ES mapping JSON at `resources/elasticsearch/media-mapping.json` and `MediaDocument`. The JSON is authoritative for the runtime mapping; annotations are informational only (`createIndex = false`).
- Added `IndexInitializer` (uses `co.elastic.clients.elasticsearch.ElasticsearchClient` directly for index/alias ops since Spring Data's `IndexOperations` is more limited for alias management).
- Added `MediaIndexer` (Media → MediaDocument projection + ES upsert/delete) and `OutboxPoller` (`@Scheduled(fixedDelayString = "${flashgif.search.outbox-poll-ms:2000}")`, batch 100).
- Added `MediaProjector` (ES doc → DTO) plus DTOs: `MediaSummary`, `SearchResponse`, `Suggestion`, `SuggestionsResponse`.
- Added `SearchSort` enum (RELEVANCE | RECENCY) with lenient `parse(String)`.
- Added `SearchService`: bool(must=multi_match+fuzziness AUTO + operator AND, filter=status=published [+ optional type]); function_score wraps it for relevance sort (popularity log1p), bypassed for recency sort. Pagination clamped (page ≤ 50, size ≤ 50).
- Added `TrendingService` with `@Cacheable("trending")` keyed by type; also exposes `asSearchResponse()` so empty `q` falls through cleanly.
- Added `SuggestionService` with `@Cacheable("suggestions")` keyed by lowercased prefix; queries `title.suggest` via `bool_prefix` multi_match across the auto-generated `_2gram` / `_3gram` subfields.
- Added `infra/cache/CacheConfig` (RedisCacheManager, per-cache TTLs, JSON value serialization).
- Added `SearchController` at `/api/v1` exposing the three endpoints with SpringDoc `@Operation` / `@Parameter` annotations so Swagger docs are useful.
- Added `MediaSeeder` (10 sample rows: cat, dance, reaction, party, etc., across gif/sticker, with realistic tag/popularity distribution).
- `./gradlew build` → BUILD SUCCESSFUL.

### End of slice state
- Compiles green. Not yet boot-verified — needs user to restart Spring Boot from the IDE (or `./gradlew bootRun`) so Flyway runs V2, IndexInitializer creates `media_v1`, and the seeder + outbox poller populate ES.
- Smoke test commands ready (next).

### End-to-end verification (user ran via Swagger UI)
- `GET /api/v1/trending` returned all 10 seeded items in **exact popularity order** (12.7 → 4.1). Confirms: Flyway V2 ran, IndexInitializer bootstrapped `media_v1` + alias, MediaSeeder inserted 10 rows + outbox events, OutboxPoller drained to ES, TrendingService queried + sorted + projected, Redis cache config didn't break serialization.
- Slice is **production-shaped** (modulo auth + real uploads): outbox → ES, cached trending, paged search, fuzzy multi_match, search-as-you-type suggestions.
- **No bugs found on first boot.** All architecture decisions held up — JSONB mapping, alias bootstrapping, outbox poller, cache config, NativeQuery API.

---

## 2026-05-21 — Session 3: Media Upload & Transformation slice

### Plan (presented to user before scaffolding)

**Endpoints**
- `POST /api/v1/media/upload` — returns `uploadId` + S3 presigned PUT URL
- `POST /api/v1/media/upload/{uploadId}/complete` — browser signals PUT finished (added beyond PRD's three for clean flow)
- `GET  /api/v1/media/status/{uploadId}` — poll transcode progress
- `POST /api/v1/media/metadata` — submit title/tags/rating → creates `media` row + emits indexing event

**End-to-end flow**
1. Client `POST /upload` with `{filename, contentType, size}`
2. Backend inserts `media_uploads` row (`AWAITING_UPLOAD`), returns presigned URL + expiry
3. Browser `PUT`s file directly to MinIO/S3 (backend never touches bytes)
4. Client `POST /complete` → backend HEADs S3 to verify object exists → status `UPLOADED` → publishes `TranscodeMessage` to RabbitMQ
5. `TranscodeWorker` (`@RabbitListener`): download original → `ffprobe` → produce mp4/webp/gif/poster → upload to S3 → status `READY` with rendition URLs
6. Client polls `/status` until `READY`
7. Client `POST /metadata` → creates `media` row + writes `media.published` outbox event in same tx
8. Existing Slice 1 outbox poller picks up the event → media is searchable within ~2s

**Two-table model**
- `media_uploads` — pipeline state machine, can fail / be abandoned
- `media` — published, searchable entity (already exists from Slice 1). Created only on successful metadata submission.
- Rationale: pipeline failures never pollute the searchable corpus.

**State machine**
```
AWAITING_UPLOAD → UPLOADED → PROCESSING → READY → PUBLISHED
       │             │            │
       │             │            └── (failure) ──┐
       │             └── (failure / S3 HEAD miss) ┤
       └── (TTL sweep, deferred to v2) ───────────┤
                                                  ▼
                                               FAILED
```
Enforced at entity level via `UploadStatus.canTransitionTo()`.

**RabbitMQ topology**
- Direct exchange `media.transcode`
- Durable queue `media.transcode.requests` with DLQ `media.transcode.dlq` (via `x-dead-letter-exchange=""` + `x-dead-letter-routing-key`)
- `default-requeue-rejected: false` so poison messages route to DLQ instead of looping
- Worker concurrency 2 (max 4), prefetch 1 (FFmpeg is CPU-bound; one transcode pegs a core)

**FFmpeg worker pipeline (per message)**
1. Mark `PROCESSING`
2. Download original to temp dir
3. `ffprobe` → width/height/duration
4. Produce 4 renditions:
   - **MP4** — `libx264 + aac`, faststart, max 720px wide
   - **WebP** — `libwebp_anim`, lossy, animated
   - **GIF** — palette-gen filter, 15 fps, 480px wide
   - **Poster** — first-frame JPEG
5. Upload renditions to `s3://flashgif-media/renditions/{uploadId}/{kind}.{ext}`
6. Persist `READY` with `rendition_urls` JSONB
7. On exception → persist `FAILED` + re-throw → Rabbit redelivery → DLQ

**Storage layout**
```
flashgif-media/
├── uploads/{uploadId}/{filename}             # original, retained for re-transcode
└── renditions/{uploadId}/{mp4,webp,gif,poster}.{ext}
```

**Validation**
- Content-Type allowlist: `video/mp4`, `video/webm`, `video/quicktime`, `image/gif`, `image/webp`
- Size ≤ 100 MB; title ≤ 200 chars; ≤ 20 tags × 64 chars
- Filename sanitised to `[a-zA-Z0-9._-]`

**CORS** — bucket policy bootstrapped from backend on startup (idempotent). Dev: `*` origins; production tightens per-env.

**Indexing reuse** — `PublishService` writes a `media.published` outbox event in the same transaction as the `media` insert. Slice 1's existing `OutboxPoller → MediaIndexer` pipeline carries it to Elasticsearch without modification. This is the architecture's first reuse moment, and it worked.

**Package layout**
```
media/
├── api/        UploadController, MetadataController, dto/
├── domain/     MediaUpload, UploadStatus, UploadService, PublishService, (Media from slice 1)
├── storage/    StorageConfig, StorageProperties, StorageService, BucketBootstrapper
├── transcode/  RabbitConfig, TranscodeMessage, TranscodeDispatcher, FFmpegRunner, TranscodeWorker
└── dev/        MediaSeeder (slice 1)
```

**Decisions deferred (not in v1)**
- S3 event-based completion (using explicit `/complete` call)
- Resumable / multipart uploads
- Auth on uploads (uploader_id nullable until Users slice)
- Background sweeper for stale `AWAITING_UPLOAD` rows
- Multi-resolution renditions (one resolution per format)
- Per-user upload quotas

**Questions surfaced & user's answers**
- FFmpeg location → **host machine** (`brew install ffmpeg`)
- Rendition set → **all four** (mp4, webp, gif, poster)
- Completion signal → **explicit `/complete`** (not S3 events)
- Media row creation → **on `POST /metadata`** (not on transcode-ready)

### Confirmed decisions (asked the user)
- **FFmpeg lives on the host** (`brew install ffmpeg` 8.1.1). Worker shells out via `ProcessBuilder`. Dockerfile for production-grade worker image can land later.
- **Renditions = mp4 (h264+aac) + animated webp + gif + poster jpeg.** All four kept; covers playback, modern browsers, fallback, and SSR previews.
- **Completion signal = explicit `POST /upload/{id}/complete`.** Simpler than S3 event notifications; no bucket-side config required. Backend HEADs S3 to verify before queuing the transcode.
- **Media row created on `POST /metadata`, not on transcode-ready.** Matches Giphy's two-step UX; keeps `media` table free of half-baked entries.

### Architecture decisions
- **Two tables: `media_uploads` (pipeline state) + `media` (published).** Pipeline failures don't pollute search. State machine enforced by `UploadStatus.canTransitionTo` + thrown `IllegalStateException`.
- **RabbitMQ topology**: `media.transcode` direct exchange → `media.transcode.requests` durable queue → DLQ via `x-dead-letter-exchange=""` + `x-dead-letter-routing-key=media.transcode.dlq`. `default-requeue-rejected: false` so poison messages flow to DLQ instead of looping forever.
- **Worker concurrency = 2 (max 4), prefetch = 1.** FFmpeg is CPU-bound; one transcode pegs a core. Per-worker prefetch=1 avoids one slow worker hogging the queue.
- **Indexing reuses Slice 1's outbox poller.** `PublishService` writes `media.published` event in the same tx as the Media insert; existing `OutboxPoller → MediaIndexer` carries it to ES. No code changes in `search/` needed — the architecture earned its keep on first reuse.
- **Idempotent metadata POST.** Re-submitting metadata for an already-PUBLISHED upload returns the existing `mediaId` instead of 409. Defensive against client retries on flaky networks.
- **CORS bootstrapped from the backend** (`PUT/GET/HEAD` from `*` — dev only; documented as such). Avoids a manual MinIO console step in the runbook.

### Steps
- `V3__media_uploads.sql` — table with status CHECK constraint, partial index on `(uploader_id) WHERE NOT NULL`, FK to `media(id)` for the eventual back-reference.
- `UploadStatus` enum with `canTransitionTo()` — illegal transitions throw at the entity level, not silently.
- `MediaUpload` entity with `@Enumerated(EnumType.STRING)` (so the enum name matches the DB CHECK constraint) and JSONB mapping on `rendition_urls`.
- `StorageProperties` (`@ConfigurationProperties("flashgif.storage")` as a record, with normalising compact constructor for defaults). `StorageConfig` builds `S3Client` + `S3Presigner` against MinIO with path-style addressing. `BucketBootstrapper` is a `@PostConstruct` bean — idempotent bucket create + dev CORS.
- `StorageService` — presign PUT, HEAD, download to `Path`, upload `Path` rendition, key helpers (`originalKey`, `renditionKey`), filename sanitiser.
- `RabbitConfig` — exchange/queue/DLQ/binding/`Jackson2JsonMessageConverter`/customised `RabbitTemplate`.
- `TranscodeMessage` (record) + `TranscodeDispatcher` publisher.
- `FFmpegRunner` — `ProcessBuilder` wrapper, `ffprobe` JSON parsing (Jackson), four transcode commands (mp4/webp/gif/poster), timeout enforcement.
- `TranscodeWorker` — `@RabbitListener`. Lifecycle: PROCESSING → download → probe → 4 transcodes → upload renditions → READY. On exception: persist FAILED with message and re-throw (Rabbit redelivery → DLQ).
- `UploadService` — orchestrates create (validates content-type allowlist, presigns URL, persists row) / markUploaded (HEAD S3 → enqueue Rabbit) / status.
- `PublishService` — atomically creates `Media`, flips upload to PUBLISHED with `mediaId`, writes `media.published` outbox event.
- DTOs: `UploadRequest`, `UploadResponse`, `UploadStatusResponse`, `MetadataRequest`, `PublishResponse`. Jakarta Bean Validation on inputs (`@NotBlank`, `@Pattern`, `@Size`).
- `UploadController` (`POST /upload`, `POST /upload/{id}/complete`, `GET /status/{id}`), `MetadataController` (`POST /metadata`). SpringDoc-annotated.
- `application.yml`: Rabbit listener concurrency (2/4), prefetch=1, no-requeue; storage props; transcode binary paths + 120s timeout.
- `./gradlew build` → BUILD SUCCESSFUL (compile + test).
- `ffmpeg --version` → 8.1.1 installed; worker is unblocked.

### End of slice state
- Compiles green. Ready for app restart + manual end-to-end smoke test (curl /upload → PUT to MinIO → /complete → poll /status → /metadata → verify /search picks it up).

### End-to-end verification (user)
- User confirmed the full pipeline works: presign → direct S3 PUT → /complete → transcode → READY with rendition URLs → /metadata → media row created → search picks it up.
- **Slice 2 done.** Two-table model + outbox-as-bridge held up — Slice 1's `OutboxPoller` indexed the new media without any change to the `search/` module.

---

## 2026-05-21 — Session 4: Users + Auth slice

### Plan (presented to user before scaffolding)

**Endpoints**
- `POST /api/v1/auth/register` — create account → returns JWT pair
- `POST /api/v1/auth/login` — credentials → JWT pair
- `POST /api/v1/auth/refresh` — refresh token → new JWT pair (rotates refresh)
- `POST /api/v1/auth/logout` — revoke refresh token
- `GET  /api/v1/users/me` — current user profile
- `PATCH /api/v1/users/me` — update display name

**Two-token model**
- **Access token** — 15 min HS256 JWT, stateless. Claims: `sub=userId`, `email`, `iat`, `exp`, `jti`. Validated locally per request, no DB hit.
- **Refresh token** — 30 days, **opaque random 256-bit token** (not a JWT), stored as SHA-256 hash in `refresh_tokens`. Rotating: each `/refresh` revokes the old and inserts a new one with `replaced_by` linking the chain.

**Why opaque refresh:** trivial revocation (delete the row) without needing a JWT blacklist. The access token's short TTL bounds the revocation lag.

**Tables (V4 migration)**
- `users (id, email citext unique, password_hash, display_name, status, ts)`
- `refresh_tokens (id, user_id FK, token_hash bytea unique, expires_at, revoked_at, replaced_by self-FK, user_agent, ip, ts)`
- **Adds `uploader_id` column to `media`** (nullable, FK to users) — missing from V2; required so the indexer can populate `uploader_username`.

**Security filter chains** (replaces Slice 0's permit-all):
- `@Order(1) /api/v1/developer/**` → API key chain (placeholder until Slice 6)
- `@Order(2) /**` → user JWT chain
  - permitAll: `/actuator/health|info`, `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/auth/**`, `/api/v1/search/**`, `/api/v1/trending`, `/api/v1/channels/*` (read-only profile)
  - authenticated: `/api/v1/media/upload`, `/api/v1/media/metadata`, `/api/v1/favorites/**`, `/api/v1/users/me`

`UserJwtFilter` (`OncePerRequestFilter`) reads `Authorization: Bearer`, validates the JWT via JJWT, builds a `UserPrincipal` (no DB hit), sets `SecurityContext`.

**Wiring into existing slices**
- `UploadService.create()` / `PublishService.publish()` read the current `UserPrincipal` from `SecurityContextHolder` and stamp `uploader_id` on the upload + media rows.
- `MediaIndexer.project()` populates `uploader_username` from `users.display_name` (currently always null). One extra `users.findById` per index event — accept the hit, cache later if needed.

**Package layout**
```
users/
├── api/        AuthController, UserController, dto/
├── domain/     User, UserRepository, RefreshToken, RefreshTokenRepository, UserService, AuthService
└── security/   JwtService, JwtProperties, UserPrincipal, UserJwtFilter
infra/security/SecurityConfig (rewritten: two filter chains, BCrypt encoder bean)
```

**Deferred (not in v1)**
- Email verification flow (assumed verified on register)
- Password reset / forgot password (no email service yet)
- OAuth providers (per Slice 0)
- 2FA, account deletion, GDPR export
- Active-session management UI
- Rate limiting on `/auth/**` (lands with Slice 6's Bucket4j)
- Per-user upload quotas

**Confirmed decisions (asked the user)**
- JWT signing → **HS256 with shared secret** (single backend verifier; switch to RS256 later if we ever federate)
- Token lifetimes → **access 15m / refresh 30d**
- Dev seeder → **yes** (`dev@flashgif.example` / `dev-password`, `@Profile("local")`, idempotent)
- Endpoint visibility → **defaults** (search/trending/suggestions/channels public; upload/metadata/favorites/me authed)

### Decisions (additional, not asked)
- **`citext` for `users.email`** — case-insensitive equality at the DB layer. One `CREATE EXTENSION` upfront vs `LOWER()` everywhere.
- **`bytea` for `refresh_tokens.token_hash`** — raw SHA-256 bytes, smaller + faster than hex string. Lookup by hashing the presented token.
- **Refresh token rotation chain via `replaced_by`** — every rotation links new → old so we have an audit trail. Compromised-token detection later can walk the chain.
- **Filter chain ordering: developer first (@Order 1), user second (@Order 2)** — most-specific `securityMatcher` wins; developer chain explicitly `denyAll()` so route reservation is enforced today even before Slice 6 implements key auth.
- **`UserJwtFilter` does NOT short-circuit on invalid token** — it clears the SecurityContext and lets `permitAll` endpoints flow through. A bad header on a public endpoint shouldn't 401.
- **Refresh stays opaque + DB-backed; access stays JWT + stateless.** Mixed-mode is intentional: revocation simplicity for the long-lived credential, zero-DB-lookup hot path for the short-lived one.
- **`UserPrincipal.currentUserId()` static helper** — call sites stay short; avoids passing `Authentication` through service signatures.

### Steps
- `V4__users_and_auth.sql` — `users` (citext email, status CHECK), `refresh_tokens` (bytea hash, replaced_by self-FK, partial index on active), `media.uploader_id` column + partial index.
- `UserStatus` enum + `User` entity (citext-backed email, `@PrePersist` timestamps).
- `RefreshToken` entity with `isActive()` helper.
- `JwtProperties` (`@ConfigurationProperties` record, ISO-8601 `Duration` defaults) + `JwtService` (issue/parse HS256 access tokens, min 32-byte secret check).
- `UserPrincipal` (extends `AbstractAuthenticationToken`, `ROLE_USER` granted, static `currentUserId()` helper) + `UserJwtFilter` (`OncePerRequestFilter`, soft-fail).
- `SecurityConfig` rewrite — `passwordEncoder` bean (BCrypt), `@Order(1) developerChain` (`denyAll()` placeholder), `@Order(2) userChain` (JWT filter, permitAll list per plan).
- `UserService` (register w/ email-uniqueness check, require, updateDisplayName, passwordMatches).
- `AuthService` (`issueForUser`, `login`, `refresh` w/ rotation + `replaced_by` link, `logout`). SHA-256 hashing for refresh storage; SecureRandom 256-bit tokens base64url-encoded.
- DTOs: `RegisterRequest`, `LoginRequest`, `RefreshRequest`, `AuthResponse`, `MeResponse`, `UpdateMeRequest` (Jakarta Validation on inputs).
- `AuthController` (`/register`, `/login`, `/refresh`, `/logout` — capture User-Agent + client IP for refresh-token metadata, honor `X-Forwarded-For`).
- `UserController` (`/me` GET + PATCH, `@SecurityRequirement("bearer-jwt")` so Swagger shows the lock icon).
- `OpenApiConfig` — added `bearer-jwt` HTTP/Bearer security scheme so Swagger UI gets an "Authorize" button.
- `UserSeeder` (`@Profile("local")`, idempotent) — seeds `dev@flashgif.example` / `dev-password`.
- `application.yml` — `flashgif.auth.{secret,issuer,access-token-ttl,refresh-token-ttl}` with safe-ish dev defaults + `FLASHGIF_JWT_SECRET` env override.
- **Cross-slice wiring:** `Media.uploaderId` field already existed; `UploadService.create` stamps `uploaderId` from `UserPrincipal.currentUserId()`; `PublishService.publish` carries it through from upload to media row.
- **`MediaIndexer` now populates `uploader_username`** by looking up `users.display_name`. One extra DB hit per index event (acceptable; cache later if it shows up in profiling).
- `./gradlew build` → BUILD SUCCESSFUL.

### Bug 1 caught at first runtime
- V4 originally tried to `ADD COLUMN uploader_id` on `media`, but **V2 had already added it** (nullable, no FK). App failed to boot with `column "uploader_id" of relation "media" already exists`.
- **Fix:** V4 now adds only the missing pieces — the FK constraint (`fk_media_uploader`) and the partial index (`idx_media_uploader`). Column declaration stays in V2.
- **Recovery:** confirmed Flyway didn't even commit a failure row to `flyway_schema_history` (Postgres rolled back the whole transaction including Flyway's bookkeeping insert). User just needs to restart.

### Bug 2 caught at second runtime
- V4 (now fixed and applied) created `users.email` as `citext`. Hibernate's strict schema validator rejected it: `found [citext (Types#OTHER)], but expecting [varchar(255) (Types#VARCHAR)]`. The String field's default JDBC type is VARCHAR; `citext` maps to OTHER (1111).
- **Two options:** annotate the field with `@JdbcTypeCode(SqlTypes.OTHER)` (works but vendor-specific binding) **or** drop citext entirely and normalize email to lowercase in app code (portable, industry-standard).
- **Chose option 2.** V5 `ALTER COLUMN email TYPE varchar(254)`. `User.setEmail` now lowercases on assignment. `UserService.normalizeEmail` is the single source of truth; `UserService.findByEmail` wraps the repo call with normalization; `AuthService.login` calls the wrapper instead of the repo directly.
- **Lesson learned (both bugs):** Testcontainers-backed integration tests would have caught both of these at `./gradlew build` time. Worth adding in a future slice that doesn't have a feature deadline.

### Bug 3 caught during smoke test
- **Symptom:** all GET requests work (`/trending` → 200, `/search` → 200, `/actuator/health` → 200). All POST requests to `permitAll` endpoints return 403 with empty body (`/auth/login`, `/auth/register`). Classic CSRF-blocking signature even though `.csrf(csrf -> csrf.disable())` is in the chain.
- **Most likely cause:** `UserJwtFilter` is annotated `@Component` AND added via `addFilterBefore(...)` in the security chain. Spring Boot auto-registers any `Filter` bean as a top-level servlet filter, so it runs twice — once outside Spring Security, once inside — with confusing interactions around POST/CSRF.
- **Fixes applied:**
  1. Added `FilterRegistrationBean<UserJwtFilter>` with `setEnabled(false)` to suppress auto-registration; filter now runs exactly once, inside the security chain.
  2. Changed filter position anchor from `UsernamePasswordAuthenticationFilter.class` (only present with form login) to `AuthorizationFilter.class` (always present).
  3. Added explicit `HttpStatusEntryPoint(UNAUTHORIZED)` so unauthenticated requests return 401 instead of Spring Security 6's default 403.
  4. Switched disable calls to method references (`AbstractHttpConfigurer::disable`) and explicitly disabled `formLogin`, `httpBasic`, `logout` for defence in depth.
  5. Added security DEBUG logging in `application-local.yml` — temporary; remove once stable.

### End of slice state
- Compiles + tests green. V4 fixed + V5 added + SecurityConfig hardened; pending user restart to verify POST auth flow.

### Bug 4 caught by debug-logged login attempt
- **Symptom (uncovered after Bug 3 fix):** `POST /auth/login` returned 500 — `column "ip" is of type inet but expression is of type character varying`. JJWT issuance and BCrypt verify worked; failure was on `INSERT INTO refresh_tokens` because `ip` column was declared as Postgres `inet` (V4) but Hibernate binds Strings as VARCHAR.
- **Fix family:** same as Bug 2 / citext. Drop the vendor-specific Postgres type, use plain `varchar(45)` (long enough for IPv6 with zone identifier). V6 migration alters the column, `RefreshToken.ip` now uses default String mapping.
- **Pattern emerging across slices 3:** every Postgres vendor-extension column (`citext`, `inet`) we used to "save typing" ended up costing more in Hibernate friction than it saved. Default to portable SQL types unless there's a strong reason to use a Postgres-specific one.

### Bug 5 caught by post-fix probe
- **Symptom:** Even after Bug 3's fix, all error responses came back as empty 401s. Spring forwards 4xx/5xx to `/error`, but my `anyRequest().authenticated()` rule was intercepting that internal dispatch and overwriting the real status/body with the `AuthenticationEntryPoint`'s empty 401.
- **Fix:** Added `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` as the first rule in the user chain. Internal ERROR dispatches now flow through to Spring Boot's `BasicErrorController` and render proper JSON error responses.
- **Cause→effect chain for this whole slice:** Bug 3 (filter double-registration) caused POSTs to 403, which masked Bug 5 (/error hijack), which masked Bug 4 (`inet` type mismatch). Each fix uncovered the next.

---

## 2026-05-21 — Session 5: Favorites + Collections slice

### Plan (presented to user before scaffolding)

**Endpoints — Favorites** (PRD-defined)
- `POST   /api/v1/favorites` — body `{mediaId}`; creates favorite + bumps `media.favorite_count`
- `DELETE /api/v1/favorites/{mediaId}` — removes + decrements
- `GET    /api/v1/users/me/favorites?page=&size=` — paged list

**Endpoints — Collections** (PRD mentions screens, no API; designed here)
- `POST   /api/v1/collections` — `{name, description?, isPublic?}`
- `GET    /api/v1/users/me/collections` — full list, owner-only
- `GET    /api/v1/users/{username}/collections` — **public-only** subset, viewable by anyone
- `GET    /api/v1/collections/{id}` — single (owner OR public)
- `PATCH  /api/v1/collections/{id}` — owner-only
- `DELETE /api/v1/collections/{id}` — owner-only (items cascade)
- `POST   /api/v1/collections/{id}/items` — owner-only, `{mediaId}`
- `DELETE /api/v1/collections/{id}/items/{mediaId}` — owner-only

**Two separate concepts**
- *Favorites* = flat per-user list (one-click save). Composite PK on `(user_id, media_id)`.
- *Collections* = named folders the user explicitly curates. Composite PK on `(collection_id, media_id)`.
Kept separate because the PRD's screens (FavoritesTab vs CollectionManager) and verbs differ; merging into "favorites = default collection" muddies the simple case.

**Schema additions (V7)**
- `users.username` — varchar(30), unique, NOT NULL, regex `^[a-zA-Z0-9_]{3,30}$`. Backfilled from email local part (sanitised + dedup'd via ROW_NUMBER, padded if <3 chars). Slice 5 (Channels) will reuse this column — pays for two slices.
- `favorites (user_id, media_id, created_at)`, composite PK
- `media_collections (id, owner_id, name, description, is_public, ts)`
- `collection_items (collection_id, media_id, added_at, position)`, composite PK. `position` reserved for future reorder API.
- Table named **`media_collections`** (not `collections`) to avoid `java.util.Collection` clash in JPA + nicer joins.

**Authorization matrix**
| Endpoint | Rule |
|---|---|
| favorites + `/users/me/favorites` | current user only |
| `POST /collections` | authenticated |
| `GET /users/me/collections` | current user only |
| `GET /users/{username}/collections` | always (public only) |
| `GET /collections/{id}` | owner OR `is_public=true` (404 otherwise — don't leak existence) |
| `PATCH/DELETE /collections/{id}` + items | owner only (404 otherwise) |

**Cross-slice effects**
- `media.favorite_count` updated **synchronously in the favorite tx** with a `SELECT … FOR UPDATE` pessimistic lock to prevent lost updates under concurrent clicks.
- **No outbox event per favorite.** Per-click ES reindex would be noisy. Instead, this slice adds the long-promised `PopularityRecomputeJob` — `@Scheduled` every 5 min, recomputes `popularity = log(1 + favorite_count*3 + view_count) * exp(-age_days/7)` for media touched since last run, writes outbox events for changed rows. Search/trending ranking stays roughly fresh without per-favorite write amplification.

**Package layout**
```
favorites/
├── api/                FavoritesController, CollectionsController, dto/
├── domain/             Favorite + FavoriteId, FavoritesService,
│                       MediaCollection, CollectionItem + CollectionItemId,
│                       CollectionsService, repositories
media/popularity/       PopularityRecomputeJob.java                     (NEW)
users/                  username field added; RegisterRequest extended; backfill
                        UserSeeder sets username "dev"
```

**Deferred (not in v1)**
- Reorder API for collection items (column reserved, no endpoint)
- Public collection share URLs / OG metadata
- Cover images on collections
- Item count in `/users/me/collections` list (would need a subquery; UI can fetch per-collection if needed)
- Collaborative collections (single owner only)
- Notifications when someone favorites your media
- "Recently favorited" / activity feed

**Confirmed decisions (asked the user)**
- Visibility default → **private**, opt-in `isPublic: true`
- Counter sync → **same tx + pessimistic row lock**
- Popularity job → **added in this slice**, every 5 min
- Extra endpoint → **`/api/v1/users/{username}/collections`** for public discoverability (requires adding `username` to users table; benefits Slice 5 too)

### Decisions (additional, not asked)
- **404 (not 403) for non-owner / non-public access.** Standard practice — don't leak the existence of private collections via differentiated status codes.
- **Idempotent endpoints throughout.** Re-favoriting a media is a no-op (no double-bump). Re-adding an existing item to a collection is a no-op. Removing a non-existent favorite is a no-op. Tolerates client retries on flaky networks; matches semantics of "saved" state.
- **`PopularityRecomputeJob` LOOKBACK > schedule interval.** 15 min lookback against a 5 min schedule means we catch anything missed across app restarts. Idempotent thanks to the outbox event being keyed by `mediaId`.
- **`MIN_CHANGE = 0.01f` skip threshold.** Tiny float drift (e.g., recomputing the same score from the same inputs) shouldn't churn the outbox + ES. Only meaningful popularity changes get republished.
- **`/users/me/**` matcher placed before `/users/*/collections`** in SecurityConfig — first-match-wins, so `me` correctly requires auth even though `me` syntactically matches the broader pattern.
- **Service-layer 404 vs HTTP-layer ambiguity.** Both ownership-failed and not-found return `NOT_FOUND` from the service; controllers don't need to distinguish.

### Steps
- `V7__favorites_collections_username.sql` — three new tables + `users.username` column with sanitised backfill (regex-strip, ROW_NUMBER dedup, length-pad), then unique constraint + format CHECK.
- `User` entity: added `username` field (unique, 30 chars). `UserRepository`: added `findByUsername` + `existsByUsername`.
- `RegisterRequest`: added `username` field with `@Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$")`. `MeResponse`: added `username`. `UserService.register` now takes username, checks both email and username uniqueness. `UserSeeder` passes `"dev"` as the dev username.
- `MediaIndexer`: switched `uploaderUsername` projection from `displayName` to actual `username` field.
- `Favorite` entity with `FavoriteId` `@IdClass` composite PK; `FavoriteRepository` with paged findByUserIdOrderByCreatedAtDesc + existsByUserIdAndMediaId + deleteByUserIdAndMediaId.
- `MediaCollection` entity, `MediaCollectionRepository` (findByOwner / findByOwnerAndPublic).
- `CollectionItem` entity with `CollectionItemId` `@IdClass`; `CollectionItemRepository` with paged finds + existence/delete by composite key.
- `MediaRepository.findByIdForUpdate` — `@Lock(PESSIMISTIC_WRITE)` for the counter-bump path. Also `findPublishedUpdatedSince` for the popularity job.
- `FavoritesService` — favorite/unfavorite with pessimistic lock on the media row before bumping `favorite_count`; idempotent.
- `CollectionsService` — full CRUD + items, owner-or-public visibility (404 otherwise).
- DTOs: `FavoriteRequest`, `FavoriteResponse`, `PagedResponse<T>` (generic mapper), `CollectionCreateRequest`, `CollectionUpdateRequest`, `CollectionResponse`, `CollectionItemRequest`, `CollectionItemResponse`.
- `FavoritesController` — `POST /favorites`, `DELETE /favorites/{mediaId}`, `GET /users/me/favorites`. SpringDoc-annotated, `@SecurityRequirement` for JWT.
- `CollectionsController` — full CRUD on `/collections`, items endpoints, `GET /users/me/collections` (own, both visibilities) and `GET /users/{username}/collections` (public-only).
- `SecurityConfig`: added `/api/v1/users/me/**` → authenticated **before** the `/users/*/collections` permitAll so `me` correctly requires auth; opened GET on `/collections/*` and `/collections/*/items` for the public-view case (service enforces actual visibility).
- `PopularityRecomputeJob` — `@Scheduled(fixedDelay=5m, initialDelay=1m)`, recomputes score for media updated in the last 15 min, writes `media.updated` outbox events only for meaningful changes.
- `./gradlew build` → BUILD SUCCESSFUL.

### End of slice state
- Compiles + tests green. Pending app restart so V7 runs (adds username + tables), `UserPrincipal` flows through favorites/collections, and the popularity job starts ticking.
- Slice 4 reuses three existing pieces of architecture: the outbox bridge (for popularity-driven reindex), `UserPrincipal.currentUserId()` (auth context propagation), and the `bearer-jwt` Swagger security scheme.

### End-to-end verification (user)
- Full sequence passed on first restart (no bugs this slice — devtools auto-reload working):
  - `/me` returns the new `username: dev` field
  - Favorite + list + unfavorite cycle, status codes correct (201/200/204)
  - Collection create + add item + list items, all 201/200
  - **Privacy boundary held:** `GET /users/dev/collections` returned `[]` while collection was private; same call returned the collection after `PATCH isPublic: true`
  - Anonymous `GET /collections/{id}` worked on public, 404 on private (not tested by user but service logic handles it)
- **Slice 4 done.** No code changes needed post-restart; this was the cleanest slice yet.

---

## 2026-05-21 — Session 6: Channels & Creator Profiles slice

### Plan (presented to user before scaffolding)

**Endpoints**
- `GET   /api/v1/channels/{username}` — public profile bundle (profile + upload count + top-5 most-favorited media)
- `GET   /api/v1/channels/{username}/media?page=&size=` — paged published-only upload history
- `PATCH /api/v1/channels/profile` — owner edits own profile via current JWT principal (no userId in path)

**Schema — extend `users`, no new table (V8)**
Per PRD every user is potentially a creator; the "CreatorEntity" referenced in the PRD is profile-scoped per user, not a separate identity. 1:1 with `users` → flat columns avoid a join on every profile read.

```sql
ALTER TABLE users
    ADD COLUMN bio          text,
    ADD COLUMN website_url  varchar(255),
    ADD COLUMN avatar_url   varchar(500),
    ADD COLUMN banner_url   varchar(500),
    ADD COLUMN social_links jsonb,         -- {twitter, instagram, tiktok, youtube, github}
    ADD COLUMN is_verified  boolean NOT NULL DEFAULT false;
```

All nullable / safe defaults → no backfill.

**Response shape**
- Profile: username, displayName, bio, websiteUrl, avatarUrl, bannerUrl, socialLinks, isVerified, createdAt
- Counts: uploadCount (`COUNT WHERE uploader_id = u.id AND status = 'published'`)
- Sidebar: `topMedia: [MediaSummary]` — top 5 by `favorite_count DESC, created_at DESC`, published only
- **No email** in channel response — that's `/users/me` only

**Cross-slice refactor**
- Move `MediaSummary` from `search.api.dto` → `media.api.dto` (it's a media DTO; search was just its first consumer). Add `MediaSummary.from(Media)` factory for the JPA-entity → DTO path. Update search imports.
- New `MediaRepository` methods:
  - `Page<Media> findByUploaderIdAndStatusOrderByCreatedAtDesc(UUID, String, Pageable)`
  - `long countByUploaderIdAndStatus(UUID, String)`
  - `List<Media> findTop5ByUploaderIdAndStatusOrderByFavoriteCountDescCreatedAtDesc(UUID, String)`

**Social links — whitelisted platforms**
Service-layer validation on the keys set: `twitter, instagram, tiktok, youtube, github`. Unknown keys rejected with 400. Values are free-form (handles, URLs, whatever the platform uses).

**Authorization**
| Endpoint | Rule |
|---|---|
| `GET /channels/{username}` | public (404 if username unknown) |
| `GET /channels/{username}/media` | public, published-only |
| `PATCH /channels/profile` | authenticated; edits current user (no userId in path) |

**Package layout**
```
channels/
├── api/
│   ├── ChannelsController.java
│   └── dto/
│       ├── ChannelResponse.java
│       └── UpdateProfileRequest.java
└── domain/
    └── ChannelsService.java   # composes User + Media reads; no entities of its own

media/api/dto/MediaSummary.java   # moved from search/api/dto/
```

**Deferred (not in v1)**
- Avatar / banner upload flow (URL fields only in v1; reusing FFmpeg pipeline is overkill for static images)
- Verified-badge admin endpoint (column exists, no API — flip via SQL until real admin auth lands)
- Follow / following relationships
- Channel analytics (views, conversion, top media over time)
- Channel theming / banner color
- Social link verification

**Confirmed decisions (asked the user)**
- Avatar/banner → **URL fields only**
- Social links → **whitelisted platforms**
- Verified badge → **column-only, no API**
- Extra → **expose top-5 most-favorited media in channel response**

### Decisions (additional, not asked)
- **`isVerified` not editable via PATCH** — `UpdateProfileRequest` deliberately omits the field. Self-verification would defeat the purpose. Future admin endpoint will be the only way to flip it.
- **PATCH semantics: null = no change, empty string = clear.** Lets clients explicitly remove a bio/avatar without nulling other fields. `socialLinks: {}` clears all links; omitting the field leaves them untouched.
- **Social link keys lowercased before validation.** Tolerant of client casing; canonical form in DB stays lowercase.
- **`/api/v1/channels/profile` PATCH doesn't collide with `/channels/{username}` GET.** Different HTTP methods; Spring MVC routes them to distinct handlers. A user with username "profile" would shadow GET — a future reserved-usernames rule will block that.
- **`MediaSummary.from(Media)` factory + DTO move.** Search and channels both project to the same shape now; the move from `search.api.dto` to `media.api.dto` makes the ownership match the data origin. Two import-line changes in `search/` were the entire cost.

### Steps
- `V8__user_profile_fields.sql` — additive `ALTER TABLE users` with six nullable columns (`is_verified` defaults to false). No backfill.
- `User` entity: added six fields, JSONB mapping for `socialLinks` via `@JdbcTypeCode(SqlTypes.JSON)`.
- **Moved `MediaSummary`** from `search/api/dto/` to `media/api/dto/`. Added `MediaSummary.from(Media)` factory. Updated 4 imports in `search/` (`SearchController`, `MediaProjector`, `TrendingService`, `SearchService`) — and 1 missed import in `SearchResponse` caught by the build.
- `MediaRepository`: added `findByUploaderIdAndStatusOrderByCreatedAtDesc` (paged), `countByUploaderIdAndStatus`, `findTop5ByUploaderIdAndStatusOrderByFavoriteCountDescCreatedAtDesc` (Spring Data Top-N derived query).
- `ChannelResponse` DTO (profile + counts + topMedia), `UpdateProfileRequest` DTO with `@URL` and `@Size` validation; all fields optional for partial PATCH.
- `ChannelsService` — read-only profile by username, paged published-media listing, partial-update with whitelisted social-link validation (`{twitter, instagram, tiktok, youtube, github}`; rejects unknown keys with 400; empty input clears).
- `ChannelsController` — three endpoints, two public (`GET /channels/{username}`, `GET /channels/{username}/media`), one authed (`PATCH /channels/profile`). Reuses `PagedResponse<T>` from favorites for the media listing.
- `SecurityConfig`: added `/api/v1/channels/*/media` to GET permitAll list.
- `./gradlew build` → BUILD SUCCESSFUL (after fixing the one missed `SearchResponse` import).

### End of slice state
- Compiles + tests green. Pending app restart so V8 runs (adds profile columns), `ChannelsController` registers, and the SecurityConfig hot-reload picks up the new permit rule.
- Slice 5 was the smallest yet — no new tables, no new entities, one DTO refactor that crossed slice boundaries cleanly.

---

## 2026-05-21 — Session 7: Developer API & Analytics slice (final backend slice)

### Plan (presented to user before scaffolding)

**Endpoints**

Key management (user-authed via JWT):
- `POST   /api/v1/auth/developer/keys` — generate a new API key. **Raw key returned exactly once.**
- `GET    /api/v1/auth/developer/keys` — list user's keys (metadata only — never the raw key)
- `DELETE /api/v1/auth/developer/keys/{id}` — revoke

Developer-facing search (API-key authed via dev chain, mirrors public endpoints):
- `GET /api/v1/developer/search`
- `GET /api/v1/developer/trending`
- `GET /api/v1/developer/search/suggestions`

Usage analytics (user-authed):
- `GET /api/v1/usage/analytics?keyId={uuid}&days={n}` — daily counts; if `keyId` omitted, aggregates across all of the user's keys

**Why a parallel dev path** — keeps the user-facing JWT chain and the third-party API-key chain cleanly separated: distinct rate limits, distinct request shapes if they ever diverge, distinct billing/analytics. Mirrors how Stripe/Giphy structure their own dev surfaces.

**API key format**
- 32-byte random, base64url-encoded → 43-char token
- Stored as **SHA-256 hash (bytea)** in `developer_keys.key_hash` — raw token never persisted (same pattern as refresh tokens)
- First 8 chars also stored as `prefix` for UI display (`fg_a1b2c3d4...`)
- Sent by clients as `Authorization: Bearer <key>` (matches JWT convention; one less header for devs to remember)

**Rate limiting**
- **Hand-rolled in-memory token bucket** (no Bucket4j dependency in v1). Per-key `ConcurrentHashMap<UUID, TokenBucket>`. Single-instance correct; scales to a single backend node.
- Default: **60 req/min**, configurable via `flashgif.developer.rate-limit-per-minute`
- 429 Too Many Requests with `Retry-After` header on exhaustion
- Bucket4j-with-Redis upgrade is a one-class swap when we go multi-instance — not worth pulling in now

**Usage recording**
- After successful dev-chain request, INCR a Redis counter: `dev:usage:{keyId}:{yyyyMMdd}`
- 35-day TTL on counters (covers a 30-day analytics window with margin)
- Atomic, fast, no DB writes on the hot path
- `last_used_at` on the key row is updated at most once every 60s per key (debounced) to avoid hot row contention

**Analytics read path**
- Read counters for the requested window from Redis
- Sum per day, sum totals
- If Redis is empty for a day (TTL'd / restart), it returns 0 — no historical archive in v1 (acceptable trade-off)
- Future: nightly roll-up job writes to a `developer_usage_daily` table for long-term retention

**Schema (V9)**
```sql
CREATE TABLE developer_keys (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          varchar(100) NOT NULL,
    key_hash      bytea NOT NULL UNIQUE,
    prefix        varchar(8) NOT NULL,                       -- "fg_a1b2c3" — for UI display
    status        varchar(16) NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','revoked')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_used_at  timestamptz,
    revoked_at    timestamptz
);
CREATE INDEX idx_developer_keys_owner ON developer_keys (owner_id);
```

**Security wiring**
- Developer chain (`/api/v1/developer/**`) — replace Slice 3's `denyAll()` placeholder with:
  - `DeveloperApiKeyFilter` (`OncePerRequestFilter`) — reads Bearer header, hashes, lookups, populates `DeveloperPrincipal` in SecurityContext
  - Rate limit + usage record done via a single follow-on filter (so 429s don't count toward usage)
  - `.authenticated()` for `anyRequest()`
- User chain unchanged; `/api/v1/auth/developer/keys` and `/api/v1/usage/analytics` remain authed via JWT (key management ≠ key usage)

**Package layout**
```
developer/
├── api/
│   ├── DeveloperKeysController.java      # /auth/developer/keys
│   ├── DeveloperSearchController.java    # /developer/search, /trending, /suggestions
│   ├── UsageAnalyticsController.java     # /usage/analytics
│   └── dto/
├── domain/
│   ├── DeveloperKey.java + status enum
│   ├── DeveloperKeyRepository.java
│   ├── DeveloperKeyService.java
│   └── UsageRecorder.java               # Redis INCR + debounced last_used_at
├── ratelimit/
│   ├── RateLimitProperties.java
│   └── TokenBucketLimiter.java          # in-memory bucket
└── security/
    ├── DeveloperPrincipal.java
    ├── DeveloperApiKeyFilter.java       # auth (chain step 1)
    └── DeveloperRateLimitFilter.java    # limit + record usage (chain step 2)
```

**Decisions made (not asked — moving fast on the final slice)**
- **Raw key shown only at creation.** Standard practice; revokeable + reissueable.
- **Token prefix `fg_`** (FlashGif). Common pattern (Stripe `sk_`, GitHub `ghp_`); helps secret scanners detect leaked keys.
- **In-memory rate limiter, not Bucket4j-Redis.** Avoids dep-resolution risk and is single-instance correct. Documented upgrade path.
- **Redis-only usage counters, no per-request audit table in v1.** Eventual consistency on history (35-day retention via TTL); accepts loss on Redis restart for v1.
- **Auth pattern: `Authorization: Bearer <key>` for dev chain.** Same shape as JWT for client simplicity. The filter looks at key length / format to distinguish (JWTs have dots; raw keys don't) — but since they hit different chains via `securityMatcher`, this disambiguation isn't actually needed.
- **Developer search endpoints delegate to existing `SearchService` / `TrendingService` / `SuggestionService`.** No new business logic; only new auth + rate-limit + analytics envelope.
- **`/api/v1/auth/developer/keys` lives in the JWT chain**, not the developer chain. Issuing/revoking keys is a user operation, not a key operation.

**Deferred (not in v1)**
- Distributed (multi-instance) rate limiting (would swap in Bucket4j-Redis)
- Per-endpoint analytics breakdown
- Latency / error-rate metrics
- Scopes (read-only / search-only / full)
- IP allowlist per key
- Webhooks
- Long-term usage archive (`developer_usage_daily` rollup job)
- Self-service quota tiers / billing

### Steps
- `V9__developer_keys.sql` — table with status CHECK, partial index `WHERE status='active'`.
- `DeveloperKeyStatus` enum, `DeveloperKey` entity (`bytea` hash, `isActive()` helper), `DeveloperKeyRepository` with `findByKeyHash` + `findByOwnerIdOrderByCreatedAtDesc`.
- `DeveloperKeyService` — `issue` (SecureRandom 32-byte → `fg_` + base64url → SHA-256 hash → save, returns raw exactly once), `list`, `revoke` (owner-check → 404 on mismatch), `resolveActive` for the auth filter.
- `DeveloperPrincipal` (mirrors `UserPrincipal` shape) + `DeveloperApiKeyFilter` (`OncePerRequestFilter`; soft-fail like the user JWT filter; auth chain still 401s via entry point).
- `RateLimitProperties` (`@ConfigurationProperties` record with sensible defaults via compact ctor) + `TokenBucketLimiter` (~50 lines: per-key `ConcurrentHashMap<UUID, Bucket>`; synchronised refill on read; returns `Retry-After` seconds on deny).
- `UsageRecorder` — Redis `INCR dev:usage:{keyId}:{yyyyMMdd}` with 35-day TTL on first INCR of the day; debounced `last_used_at` update via `@Transactional(REQUIRES_NEW)` (avoids polluting calling tx).
- `DeveloperRateLimitFilter` — runs after auth filter; consults bucket; 429 + `Retry-After` on deny; on allow, records usage *then* lets request proceed (so 429s don't count toward analytics).
- `SecurityConfig` — replaced Slice 3's `denyAll()` placeholder with real developer chain: `developerApiKeyFilter` + `developerRateLimitFilter` before `AuthorizationFilter`. Also wired `FilterRegistrationBean` disables for the two new `@Component` filters (same fix pattern as Bug 3 from Slice 3).
- `DeveloperSearchController` (`/api/v1/developer/{search,trending,search/suggestions}`) — pure delegation to existing `SearchService` / `TrendingService` / `SuggestionService`. Zero new business logic.
- `DeveloperKeysController` (`POST /auth/developer/keys`, `GET`, `DELETE /{id}`) + `IssuedKeyResponse` (raw key field with "shown once" Swagger description) + `KeyResponse` (metadata-only).
- `UsageAnalyticsController` (`GET /usage/analytics?keyId=&days=`) — day-by-day fan-out across user's keys (or filter to a specific key); 35-day window cap matches Redis TTL.
- `application.yml`: `flashgif.developer.requests-per-minute: 60`, `burst-capacity: 60`.
- `./gradlew build` → BUILD SUCCESSFUL on first try.

### End of slice state
- Compiles + tests green. **Slice 6 done — backend is feature-complete per PRD.** Pending app restart so V9 runs, developer chain takes over from the denyAll placeholder, and the rate-limit bucket map initialises.
- Six slices, six migrations, ~75 tasks tracked, 5 bugs caught (all in Slice 3's auth wiring), zero post-restart regressions in Slices 4-6.

### Backend completeness checklist (per PRD)
| Feature | Endpoints | Slice |
|---|---|---|
| 1. Advanced GIF Search | ✅ search, trending, suggestions | 1 |
| 2. Media Upload & Transformation | ✅ upload, complete, status, metadata | 2 |
| 3. User Collections & Favorites | ✅ favorites + collections CRUD | 4 |
| 4. Developer API & Integration | ✅ keys + dev search + usage analytics | 6 |
| 5. Creator Profile & Channel | ✅ channel read + profile update | 5 |
| Auth (implied by 2/3/4/5) | ✅ register, login, refresh, logout, /me | 3 |

Next: frontend (Next.js) and mobile (Swift, Kotlin) — not in this scope but the API contract is locked at `/v3/api-docs.yaml`.

---

## 2026-05-22 — Session 8: Web Slice 1 — Next.js scaffold + Search & Discovery

### Plan (presented to user before scaffolding)

First web slice mirrors backend Slice 1: public-facing search + trending. Pages render anonymously (no auth coupling) — the simplest way to stand up the frontend and prove the API contract end-to-end.

**Stack**
- Next.js 14 App Router + TypeScript, pnpm, dev port `:3000`
- Tailwind CSS
- React Query (TanStack v5) for server state
- **shadcn/ui** for primitives (copy-paste, Tailwind-based, we own them)
- **openapi-typescript** for TS types from `docs/openapi.yaml`; thin hand-written `fetch` wrapper on top (no runtime client lib)
- **Zustand** for local UI state (~1KB; React Query owns all server state)
- `react-masonry-css` for the grid
- **Playwright** for e2e smoke tests (added per user request — pays off across all future web slices)

**Pages in this slice**
- `/` — Home: trending masonry + type chips (gif / sticker). Server component for SEO + fast first paint.
- `/search?q=&type=&sort=&page=` — Search results: infinite-scroll masonry. Client component.
- SearchBar with debounced autocomplete dropdown lives in the `Header` (everywhere).

**Repo layout**
```
web/
├── app/
│   ├── layout.tsx, page.tsx (Home), search/page.tsx
├── components/
│   ├── layout/  (Header, Footer)
│   ├── search/  (SearchBar, TypeChips)
│   ├── media/   (MediaCard, MasonryGrid)
│   └── ui/      (shadcn/ui generated)
├── lib/
│   ├── api/     (types.ts [generated], client.ts, endpoints.ts)
│   ├── query/   (keys.ts, hooks.ts)
│   └── env.ts
├── tests/e2e/   (Playwright)
└── (config: next.config.mjs, tailwind.config.ts, tsconfig.json, package.json)
```

**Backend change required**
- Add CORS config to allow `http://localhost:3000` during dev. Spring Security has no CORS today; without it, browser blocks `/api/v1/*` calls.

**Local dev runtime**
```
Terminal 1: cd ops && docker compose up -d
Terminal 2: cd backend && ./gradlew bootRun       # :8080
Terminal 3: cd web && pnpm dev                    # :3000
```

**Deferred (later web slices)**
- Slice 2 (Web): Auth + shared layout
- Slice 3: Upload flow
- Slice 4: Favorites + Collections UI
- Slice 5: Public channel view
- Slice 6: Developer dashboard

**Confirmed decisions (asked the user)**
- API client → **openapi-typescript + thin fetch wrapper** (types only, no runtime client lib)
- Component library → **shadcn/ui + Tailwind** (we own the components)
- Client state → **Zustand** (server state stays in React Query)
- Extras → **Add Playwright e2e** smoke tests from the start

### Decisions (additional, not asked)
- **Hand-write minimal Next.js scaffold, skip `create-next-app`.** Avoids interactive prompts and lets us pin exact versions + drop only the files we need.
- **Drop the `shadcn/ui` CLI; copy canonical files directly.** The CLI just writes templates we already know. One less init step + we own from line one.
- **`<img>` for animated formats, not `next/image`.** Next/Image's optimiser doesn't preserve animation for GIF/animated WebP. Plain `<img>` + `loading="lazy"` is the right call.
- **`useSearchParams` in `SearchBar` requires a `<Suspense>` boundary** — caught at build time. Wrapped in `<Suspense>` inside Header with a non-interactive fallback that matches the layout footprint (no CLS).
- **pnpm 11 quirk:** `pnpm.onlyBuiltDependencies` moved out of `package.json` into `pnpm-workspace.yaml` under `allowBuilds`. Created the workspace file to allowlist `unrs-resolver`'s postinstall (transitive native binding via eslint-config-next).
- **CORS uses env-var allowlist** (`FLASHGIF_WEB_ORIGINS`) with `http://localhost:3000` as the dev default; production sets the real origin via env.

### Steps
- Next.js 14 scaffold: `package.json` (pinned deps), `tsconfig.json` (path alias `@/*`), `next.config.mjs`, `tailwind.config.ts`, `postcss.config.mjs`, `next-env.d.ts`, `.env.local.example`, `pnpm-workspace.yaml`.
- `src/app/globals.css` with shadcn CSS variables (violet primary, hot-pink accent FlashGif theme) + dark mode + masonry overrides.
- API client: `src/lib/api/client.ts` (`apiFetch<T>` with query/token/JSON/ApiError mapping), `endpoints.ts` (hand-typed Slice-1 shapes + `getTrending`/`search`/`getSuggestions`), stub `types.ts` ready for `pnpm gen:api`.
- React Query: `src/lib/query/{keys,hooks,QueryProvider}.ts(x)` — `useTrending`/`useSearch`/`useSuggestions` with debounce + infinite pagination.
- shadcn-style primitives: `Button` (CVA variants) + `Input`.
- Components: `Header` (logo + suspended `SearchBar` + login/signup placeholders), `Footer`, `SearchBar` (debounced autocomplete dropdown), `TypeChips`, `MediaCard` (hover overlay + disabled favorite stub for Slice 4), `MasonryGrid` (responsive 1-4 cols).
- Pages: `app/layout.tsx` (QueryProvider + Header + Footer shell), `app/page.tsx` (SSR trending, `revalidate = 60`) + `HomeClient.tsx` (TypeChips switcher), `app/search/page.tsx` (force-dynamic) + `SearchClient.tsx` (infinite scroll via IntersectionObserver + URL-state filters).
- **Backend:** added `CorsConfigurationSource` bean + `cors(Customizer.withDefaults())` on both filter chains. Env-var allowlist; backend recompiles green.
- Playwright: `playwright.config.ts` (auto-starts `pnpm dev`), `tests/e2e/trending.spec.ts`, `tests/e2e/search.spec.ts`.
- `pnpm build` → BUILD SUCCESSFUL after one Suspense fix. Bundle: **108 KB First Load JS**, 597 B per-page for `/`, 1.26 kB for `/search`.

### End of slice state
- `pnpm build` green; Next.js production output verified.
- Backend restart needed to pick up the new CORS config (devtools should auto-reload).
- Smoke flow: open `http://localhost:3000`, see trending masonry; type "happy" in search → results; `pnpm test:e2e` from `web/` runs the Playwright suite (needs backend up).

### End-of-session state
- Backend skeleton compiles and tests pass.
- App will boot once `docker-compose up` runs (it needs Postgres/ES/Redis/RabbitMQ); we have **not** booted it yet because feature code is the next slice.
- Swagger UI will be reachable at `http://localhost:8080/swagger-ui.html` once we run.
- Next slice: **Search & Discovery** — Postgres `media` table (V2 migration), `MediaDocument` (ES), `SearchController` with the three endpoints, outbox poller. That's session 2.

### Bug cluster caught by Playwright e2e (post-Web-Slice-1)
- **Bug A — camelCase vs snake_case wire format.** TS DTOs typed with snake_case based on backend `@Schema(name = "...")` annotations, but those annotations only affect OpenAPI docs — Jackson actually emits camelCase. Fixed by switching TS shapes + `MediaCard` refs to camelCase. **Follow-up:** add `spring.jackson.property-naming-strategy=SNAKE_CASE` server-side to align JSON with the documented schema names before mobile clients land.
- **Bug B — `revalidate = 60` cached an empty initial during the bug window.** Once `getTrending()` SSR-failed (because of Bug C), Next.js cached `initial = []` for 60s and kept serving an empty home page. Switched `/` to `export const dynamic = "force-dynamic"` until the data path is stable; re-enable ISR later.
- **Bug C — Redis cache serializer loses generic type info.** `@Cacheable("trending")` / `("suggestions")` return `List<MediaSummary>` / `List<Suggestion>`. The `GenericJackson2JsonRedisSerializer` initialised with the default web `ObjectMapper` doesn't embed `@class`, so reads come back as `List<LinkedHashMap>` and the HTTP serializer subsequently throws `IllegalArgumentException: object is not an instance of declaring class` when it calls record accessors on a Map. Fix: dedicated cache `ObjectMapper` with `activateDefaultTyping(...)` + `BasicPolymorphicTypeValidator` allowing `com.flashgif.`, `java.util.`, `java.time.`. Keeps `@class` out of HTTP responses (separate mapper). Requires backend restart + Redis FLUSHDB.
- **Bug D — `<img loading="lazy">` with broken src + `h-auto` collapsed tiles to 0×0.** Playwright `toBeVisible()` requires non-zero dimensions. Tiles existed in the DOM but broken `example.invalid` URLs left them with no intrinsic dimensions; `h-auto` made the container 0px tall. Fix: reserve `aspect-ratio` on the container so it has dimensions regardless of image load. Bonus: no layout shift on slow networks.
- **Diagnostic lesson:** the first 500 surfaced as `object is not an instance of declaring class` — opaque if you haven't seen it before. Runbook entry: this error almost always means a cached value with stale or lossy type info is being serialized to an HTTP response.
- **Bug E — curly-quote heading vs straight-quote regex.** `SearchClient` renders `Results for &ldquo;{q}&rdquo;` (typographic quotes) but the test regex used ASCII straight quotes. Fixed by matching `/Results for .*cat/` without quote binding. The heading itself is correct — typographic quotes are the right UX choice.

### Final smoke verification
- All 5 Playwright tests pass in 5.2s. Slice 1 of the web is fully end-to-end green.
- **Five distinct bugs caught across the cluster**, each masking the next: A (camelCase) and C (cache typing) caused the API 500s, which caused B (ISR cached the empty result), which masked D (img sizing) and E (curly quotes) on the test side.
- **Lessons for future web slices:**
  1. Reserve aspect ratio for media tiles — already done in MediaCard.
  2. Use lenient regex for text content that might be typographic.
  3. Don't enable ISR until the data path is proven stable.
  4. The cache `ObjectMapper` fix (CacheConfig) is now universal — future cached endpoints inherit it.

---

## 2026-05-22 — Session 9: snake_case JSON alignment (API contract follow-up)

### Plan
- Set `spring.jackson.property-naming-strategy: SNAKE_CASE` in `application.yml` so Jackson emits and accepts `snake_case` field names globally — matches the `@Schema(name = ...)` annotations the OpenAPI docs already promise.
- **Scope of impact:** every JSON request/response on the public API. Today's only consumer is the web (we control it). Mobile + third-party API key clients are future and will consume the corrected spec.
- **Per-DTO knock-on:** `renditionUrls`→`rendition_urls`, `viewCount`→`view_count`, `accessToken`→`access_token`, `displayName`→`display_name`, etc.
- **Boolean gotcha to watch:** Lombok strips the `is` prefix on getters for `boolean isXxx` fields, so Jackson sees the property as `xxx`. `User.isVerified` is exposed as JSON `verified`, **not** `is_verified`. Same goes for `isPublic` in `MediaCollection`. Fine — just don't be surprised.
- **Outbox payload safety:** `Map<String, String>` keys are NOT transformed by Jackson's `PropertyNamingStrategy` (only bean properties are). `OutboxPublisher.publish(..., Map.of("mediaId", ...))` keeps `mediaId` literal. `OutboxPoller.extractMediaId` continues to read `payload.get("mediaId")`. No change needed.
- **Cache `ObjectMapper` is separate** (Bug C fix). It does NOT inherit the global naming strategy. Cache values are internal only; consistent self-roundtrip is all that matters.
- **Redis FLUSHDB required** after backend restart — cached values from the camelCase era would deserialize fine but then re-serialize via the new HTTP mapper as snake_case, breaking key consistency on subsequent reads if the cache layer ever caches deserialized DTOs (it currently doesn't, but better safe).
- **Web side:** revert `endpoints.ts` types to snake_case (where they were before Bug A's fix), update `MediaCard.tsx` field refs.
- **Verification:** `pnpm test:e2e` should still be green; backend's existing unit tests use MockMvc which goes through the same Jackson config so they remain accurate.
- **Cleanup deferred:** `@Schema(name = "...")` annotations are now redundant with the global strategy. Worth a one-shot delete-pass when convenient; doesn't affect correctness.

### Steps
- Added `spring.jackson.property-naming-strategy: SNAKE_CASE` to `application.yml`.
- Web side: reverted `endpoints.ts` MediaSummary + SearchResponse types to snake_case (now matches the OpenAPI `@Schema(name = ...)` annotations); updated `MediaCard.tsx` to read `media.rendition_urls`.
- `docker exec flashgif-redis redis-cli FLUSHDB` (any pre-change cached values would have been camelCase under the new mapper).
- Verified backend response shapes:
  - `/api/v1/trending[0]` → `[content_rating, created_at, favorite_count, height, id, rendition_urls, tags, title, type, view_count, width]`
  - `/api/v1/search` → top-level `[items, page, size, took_ms, total]`
  - `/api/v1/search/suggestions` → `{items: [{text, source}]}` (single-word field names unchanged)
- `pnpm test:e2e` → 5/5 green in 5.6s.

### End-of-slice state
- Public API wire format is now snake_case, aligned with the OpenAPI spec that's been promising it all along.
- Cache `ObjectMapper` (Bug C fix from Session 8) remains on its own — still camelCase internally, doesn't leak to the wire.
- `Map<String, String>` outbox payload keys preserved as-is (Jackson naming strategy doesn't apply to Map keys); `OutboxPoller.extractMediaId("mediaId")` continues to work without changes.
- **Follow-up still open:** delete the now-redundant `@Schema(name = "...")` annotations across DTOs. Cosmetic; no rush.

---

## 2026-05-22 — Session 10: Web Slice 2 — Auth + shared layout

### Plan (presented to user before scaffolding)

Unlocks the disabled Login/Sign-up buttons in the Header, and gates the future Upload, Favorites, Channel-settings, and Dev-dashboard slices.

**Pages**
- `/login` — email + password
- `/register` — email + username + password + display name
- Header gains `<UserMenu>` (avatar + dropdown: "My profile", "Log out") when authenticated; "Log in" / "Sign up" otherwise.

**Auth model — httpOnly cookies via Next.js Route Handler proxy**
Browser never sees raw access/refresh tokens. Same-origin from the browser's view (`/api/...` is Next.js); cross-origin Spring call happens server-side.

```
Browser  → POST /api/auth/login {email, password}
Next Route Handler  → Spring /api/v1/auth/login
       Set-Cookie: flashgif_access  (HttpOnly, Secure, SameSite=Lax, 15min)
       Set-Cookie: flashgif_refresh (HttpOnly, Secure, SameSite=Lax, 30d, Path=/api/auth)
```

Subsequent: `Browser → /api/users/me → Route Handler reads cookie → Spring /api/v1/users/me with Bearer`. On 401: `apiFetch` interceptor calls `/api/auth/refresh` once (refresh cookie attached); on refresh-success retries original request; on refresh-failure redirects to `/login?next=...`.

**SSR pre-fill for `useMe()`** (per user request)
Root `layout.tsx` (server component) reads the cookie via `next/headers#cookies()`, calls backend `/api/v1/users/me` server-side, and seeds React Query with the result. Header renders the correct state on first paint — no Login → UserMenu flash.

**Route Handlers in this slice**
```
app/api/
├── auth/
│   ├── login/route.ts     POST → Spring login + set 2 cookies
│   ├── register/route.ts  POST → Spring register + set 2 cookies
│   ├── refresh/route.ts   POST → Spring refresh + rotate 2 cookies
│   └── logout/route.ts    POST → Spring logout + clear 2 cookies
└── users/me/route.ts      GET  → Spring /users/me with Authorization from cookie
```

**Component tree additions**
```
app/
├── login/page.tsx, register/page.tsx
components/
├── auth/  LoginForm, RegisterForm, UserMenu
└── layout/Header.tsx  (replaces disabled buttons with UserMenu or Login/Sign-up)
lib/
├── auth/  cookies.ts (names + options), server.ts (readAccessToken, getCurrentUser)
├── api/   auth.ts (client wrappers), client.ts (add 401-refresh-retry)
└── query/ hooks.ts  (useMe, useLoginMutation, useRegisterMutation, useLogoutMutation)
```

**Forms — React Hook Form + Zod** (per user choice)
Resolver-based validation matching backend constraints (email regex, password ≥12, username `[a-zA-Z0-9_]{3,30}`, display name 1–50). Schema reused across Slice 3+ forms.

**Dev login hint** (per user choice)
When `NEXT_PUBLIC_ENV=local`, `/login` shows a one-line tip with the seeded credentials (`dev@flashgif.example` / `dev-password`). Auto-hidden in any other env.

**Tests (Playwright)**
- Register fresh user → header shows UserMenu with display name
- Logout → header reverts to Login/Sign-up
- Login as seeded dev user → header shows UserMenu
- Refresh round-trip is harder to test cleanly (would need to fake JWT expiry); deferred

**Confirmed decisions (asked the user)**
- Token storage → **httpOnly cookies + Route Handler proxy**
- Form library → **React Hook Form + Zod**
- Login UX → **dev creds hint when `NEXT_PUBLIC_ENV=local`**
- Extra → **SSR pre-fill via Server Component reading cookie + seeding React Query**

**Deferred (later slices)**
- Forgot password / password reset (backend has no email service)
- Email verification UI
- OAuth / 2FA / account deletion
- Profile settings UI (lives in Web Slice 5 — channel)

### Decisions (additional, not asked)
- **Public endpoints still go direct to Spring (CORS-allowed); only authed endpoints proxy through Next.js Route Handlers.** Pattern: `apiFetch` for direct → Spring, `authedFetch` for same-origin → Next.js. Avoids a needless hop on the 90%-of-traffic search/trending path.
- **`authedFetch` interceptor only retries non-auth paths on 401.** A 401 from `/api/auth/refresh` itself shouldn't trigger another refresh attempt; that would loop.
- **Server-side `getCurrentUser()` bypasses the Next.js Route Handler** — direct server-to-Spring call with the cookie token attached. Saves a same-origin hop during SSR.
- **`QueryProvider` accepts an optional `seed`** to pre-populate the React Query cache from server components. Replaces the "loading skeleton" UX with a fully-correct first paint.
- **Logout = revoke on backend + clear both cookies + `router.refresh()`.** The refresh re-runs `RootLayout`'s `getCurrentUser()` so the Header updates without a full reload.
- **Form-scoped selectors in e2e** (`getByRole("form", { name: "Log in" })` and `page.locator("header")...`) — disambiguates the form submit button from the same-text header nav button. Real DOM quirk: shadcn `<Button>` inside Next.js `<Link>` renders as both a `<button>` and an `<a>` in the accessibility tree.

### Steps
- Installed `react-hook-form@7.76`, `@hookform/resolvers@5.2`, `zod@4.4`.
- `lib/auth/`: `cookies.ts` (name constants + option helpers; `secure` in prod, `sameSite=Lax`; refresh path-scoped to `/api/auth`), `server.ts` (SSR `readAccessToken()` + `getCurrentUser()` direct-to-Spring), `session.ts` (set/clear cookies from backend `AuthResponse`), `schemas.ts` (Zod schemas mirroring backend validation: email, password ≥12, username `[a-zA-Z0-9_]{3,30}`, display name 1-50).
- Route Handlers under `app/api/`: `auth/{login,register,refresh,logout}/route.ts`, `users/me/route.ts`. Refresh path clears cookies on failure so the client can re-login cleanly.
- `lib/api/`: `authed.ts` (same-origin fetch with 401-refresh-retry, skips `/api/auth/*` to avoid loops), `auth.ts` (typed wrappers for the 4 auth endpoints + `/me`).
- React Query: `useMe(initialData?)` (swallows 401 to null), `useLoginMutation/useRegisterMutation/useLogoutMutation` — all call `router.refresh()` post-success so server components re-run.
- Pages: `/login`, `/register` (server components — redirect home if `getCurrentUser()` returns non-null). Forms in `components/auth/`: `LoginForm` (RHF + Zod, dev-creds hint when `NEXT_PUBLIC_ENV=local`), `RegisterForm` (RHF + Zod, surfaces backend 409 detail messages).
- `UserMenu` — avatar with initials + dropdown (My channel, Log out); takes `initial` prop seeded by SSR. Header rewritten to take `user` prop + render `UserMenu` (which internally hosts the logged-out Login/Sign-up Links).
- `RootLayout` now async — fetches user via `getCurrentUser()`, passes to `Header` and seeds `QueryProvider`. SSR renders the correct state on first paint.
- Playwright `auth.spec.ts` (3 specs: dev login + logout round-trip, fresh registration, redirect-when-authed).
- `pnpm build` → 12 routes (2 new pages + 5 Route Handlers), 139 KB First Load JS on `/login` (jumps from 108 → 139 because RHF + Zod ship). All good.
- `pnpm test:e2e` → **8/8 green** in 6.6s.

### End-of-slice state
- Auth is fully wired end-to-end: signup → cookie → SSR-rendered logged-in header → /me hooks → logout → cookies cleared → header reverts.
- The `authedFetch` + 401-refresh-retry primitive is ready for the upcoming Upload (Web 3), Favorites (Web 4), Channel-settings (Web 5), and Dev-dashboard (Web 6) slices.
- Token storage matches `architecture.md`: httpOnly cookies, never touchable from JS, Route Handlers as the same-origin proxy.
- Total bundle for the auth pages is 139 KB First Load — well under the 200 KB Lighthouse "good" threshold.

---

## 2026-05-22 — Session 11: Web Slices 3-6 (compressed plan, batched build)

User said "implement the remaining web slices" — single push for slices 3-6. Plans compressed; cross-cutting style/error/loading polish deferred. Goal is functional + smoke-tested, not pixel-perfect.

### Web Slice 3 plan — Upload flow

**Trigger:** new "+ Upload" button in Header (visible only when authenticated) → opens `UploadModal`.

**Modal flow:**
1. **Pick** — drag-and-drop or click; client-side validation (size ≤100 MB, mime in `[video/mp4, video/webm, video/quicktime, image/gif, image/webp]`)
2. **Reserve** — POST `/api/media/upload` `{filename, content_type, size}` → `{upload_id, presigned_url, expires_at}`
3. **PUT to S3** directly with the file body (bypasses our backend entirely — bandwidth doesn't traverse Next.js)
4. **Complete** — POST `/api/media/upload/[uploadId]/complete` → backend HEADs S3, publishes transcode job
5. **Poll** — GET `/api/media/status/[uploadId]` via React Query `refetchInterval` (1.5s while `PROCESSING`, stop on `READY`/`FAILED`)
6. **Metadata** — RHF + Zod form: title, tags (comma-separated, max 20 × 64ch), content rating (G/PG/PG13/R), optional description
7. **Publish** — POST `/api/media/metadata` → close modal, toast "Published!", `router.push('/channels/[me]')`

**Route Handlers** (all auth-required, all proxy through Spring with Bearer from cookie):
- `app/api/media/upload/route.ts` (POST)
- `app/api/media/upload/[uploadId]/complete/route.ts` (POST)
- `app/api/media/status/[uploadId]/route.ts` (GET)
- `app/api/media/metadata/route.ts` (POST)

**Components:** `UploadModal`, `Dropzone` (HTML5 dnd, no extra lib), `ProcessingStatus` (shows progress states), `MetadataForm`.

**Deferred:** chunked/resumable upload, avatar/banner upload (Slice 5), batch upload, video preview during transcode, upload retry on transient failure.

### Web Slice 4 plan — Favorites + Collections UI

**Heart button on MediaCard** — wire the disabled placeholder. Optimistic update via React Query (`onMutate` snapshot + rollback on error). Visual: filled heart = favorited, outline = not.

**Pages:**
- `/favorites` — auth-required; paged grid of own favorites (uses `apiFetch` not `authedFetch` since GET endpoint via Next.js proxy)
- `/collections` — auth-required; grid of own collections (private + public); "+ New collection" button
- `/collections/[id]` — public if `is_public`, else owner-only; items grid; edit/delete for owner

**Add-to-collection UX:** secondary button on `MediaCard` (next to heart) opens a small popover listing user's collections with checkboxes + "+ New collection…" inline.

**Components:** `FavoriteButton` (replaces disabled stub), `AddToCollectionPopover`, `CollectionCard`, `NewCollectionDialog`, `CollectionsGrid`.

**Route Handlers:** proxy `app/api/favorites/route.ts` (POST), `app/api/favorites/[mediaId]/route.ts` (DELETE), `app/api/users/me/favorites/route.ts` (GET paged), `app/api/collections{/route.ts, /[id]/route.ts}` (CRUD), `app/api/collections/[id]/items/{route.ts, [mediaId]/route.ts}`, `app/api/users/me/collections/route.ts` (GET).

**Deferred:** reorder items (backend column reserved but no API), share URLs/OG, public discovery page (`/users/[username]/collections` consumes the existing public endpoint, won't dedicate a page yet).

### Web Slice 5 plan — Public channel view + own-profile settings

**Public read:** `/channels/[username]` — server-rendered, no auth required. Calls Spring `/api/v1/channels/[username]` direct (no proxy needed for public reads). Renders banner/avatar/bio/website/social-links/verified-badge + paged upload grid (client-side) + top-5 sidebar from the same payload.

**Owned edit:** `/settings/profile` — auth-required server component (redirect to /login if not). RHF + Zod form for `display_name`, `bio`, `website_url`, `avatar_url`, `banner_url`, `social_links` (5 known platforms as separate inputs: twitter, instagram, tiktok, youtube, github). Submits via `PATCH /api/channels/profile` (Route Handler proxy).

**Components:** `ChannelHeader` (banner + avatar + meta), `ChannelGrid` (paged uploads), `TopMediaSidebar`, `ProfileForm`.

**Route Handler:** `app/api/channels/profile/route.ts` (PATCH only). Public reads stay direct to Spring.

**Deferred:** avatar/banner upload UI (URL field only per backend decision); verified badge can't be self-flipped (column-only per backend); social link verification.

### Web Slice 6 plan — Developer dashboard

**Pages:**
- `/dev` — landing: brief description, "New API key" button, key list table (name, prefix, status, last_used_at, revoke)
- `/dev/keys/new` — issue key form (just `name`), success page shows **raw key exactly once** with copy-to-clipboard, "Done" returns to /dev
- `/dev/usage` — analytics: key picker (or "All keys"), days filter (7/30), recharts `<BarChart>` of `day → count`, total summary

**Chart library:** `recharts` (per user choice).

**Components:** `KeyList`, `NewKeyForm`, `IssuedKeyDisplay`, `UsageChart`, `KeyPicker`.

**Route Handlers:** proxy `app/api/auth/developer/keys/route.ts` (POST + GET), `app/api/auth/developer/keys/[id]/route.ts` (DELETE), `app/api/usage/analytics/route.ts` (GET).

**Deferred:** webhooks, scopes UI, IP allowlist, request-log drilldown (we only show daily counts).

### Cross-slice conventions for this batch
- Modals = Tailwind absolute-positioned div with backdrop (no extra radix-dialog lib for speed — can swap later)
- Optimistic updates only where the operation is idempotent and the rollback is cheap (favorite/unfavorite)
- Inline form errors only — no toast lib in this batch (deferred per user choice)
- Targeted Playwright: one happy-path per slice, no exhaustive coverage

### Confirmed decisions (asked the user)
- Upload UX → **modal triggered from header "+" button**
- Chart library → **recharts**
- Extras → **proceed as-is, no toast lib / no error-boundary polish this round**

### Decisions (additional, not asked)
- **Shared `proxyToBackend` helper in `lib/auth/proxy.ts`** — every authed Route Handler is now a 3-line forward. Cut ~10 handlers from ~10 lines each down to ~3.
- **`MediaTilePlaceholder` for favorites + collection items.** Backend has no `GET /api/v1/media/{id}` yet — we can favorite a media-id but can't rehydrate the full row from id alone. Renders id + timestamp placeholders for now. Follow-up: backend should add `GET /api/v1/media/{id}` so favorites/collection-items pages can show real `<MediaCard>`s.
- **`channels.ts` is a mixed module (no `"use client"` directive)** — server components call `getChannel`/`getChannelMedia`, clients call `updateProfile`. Per-module directive omitted so server-safe pure functions can be imported by server components.
- **`useMyFavoriteIds()` does paged scan + builds a `Set<string>`** to power per-`MediaCard` heart state. Bounded loop (50 pages × 100 items = 5000 favorites cap). At scale we'd add a dedicated `/api/v1/users/me/favorite-ids` endpoint.
- **Backend `isVerified` field gotcha** — Lombok generates `isVerified()` getter, Jackson sees property as `verified` (strips `is` prefix). Wire emits `verified`, not `is_verified`. `ChannelResponse` TS type uses `verified: boolean` to match.
- **Optimistic favorite toggle** with snapshot + rollback on error. Card flips instantly; backend sync is hidden.

### Steps
- **Slice 3 (upload):** Route Handlers for `/api/media/{upload, upload/[id]/complete, status/[id], metadata}` (bearer-from-cookie). `lib/api/media.ts` client wrappers + `lib/upload/schemas.ts` (Zod metadata + file validation). `UploadModal` (3-stage: dropzone → upload+poll → metadata form) + `UploadButton` in Header (auth-only).
- **Slice 4 (favorites + collections):** `lib/auth/proxy.ts` helper. 7 Route Handlers (favorites + collections + items + me listings). `lib/api/favorites.ts` + `lib/query/favoritesHooks.ts` with optimistic favorite mutation. Wired heart button on `MediaCard` (fills + opacity-1 when favorited). `/favorites` page with paging. `/collections` page with `NewCollectionDialog`. `/collections/[id]` page with owner-only edit/delete + visibility toggle. UserMenu got Favorites / Collections / Settings / Developer links.
- **Slice 5 (channel + profile):** `lib/api/channels.ts` mixed module (server-safe public reads + client-only PATCH). `PATCH /api/channels/profile` Route Handler. `/channels/[username]` server-rendered page (`notFound()` on 404 from backend) → `ChannelClient` with banner + avatar + bio + social links + verified badge + paged upload feed + top-5 sidebar. `/settings/profile` page seeded by SSR channel read → `ProfileForm` (controlled state, 5 social platforms as separate inputs).
- **Slice 6 (developer dashboard):** installed `recharts@3.8.1`. Route Handlers for `/api/auth/developer/keys{,/[id]}` and `/api/usage/analytics`. `lib/api/developer.ts` + `lib/query/devHooks.ts`. `/dev` page with key list table + inline revoke. `/dev/keys/new` → `NewKeyClient` (form → `IssuedKeyDisplay` with raw key + copy-to-clipboard). `/dev/usage` with key picker + 7/30-day toggle + recharts `BarChart`.

### End-of-slice state (Web 3-6)
- `pnpm build` → 30 routes (12 pages + 18 Route Handlers). Largest bundle is `/dev/usage` at 210 KB First Load (recharts). All others under 140 KB.
- `pnpm test:e2e` → **8/8 green** in 8.3s. The 8 existing search/auth tests still pass. No new e2e for the new pages (per "proceed as-is" choice).
- **Frontend is feature-complete per PRD:** every backend slice now has at least one consuming UI page.

### Known limitations + follow-ups
- **`/favorites` + `/collections/[id]` show ID-only placeholders** — backend missing `GET /api/v1/media/{id}`. Backend follow-up: add the endpoint, then web replaces `MediaTilePlaceholder` with `<MediaCard>`.
- **Avatar/banner are URL inputs only** (matches backend slice 5 decision). A presigned-upload flow specifically for static images is a future addition.
- **No e2e for upload, favorites, channel, dev** — coverage is targeted at the most-trafficked paths (search + auth). Half-day project per slice to add.
- **`useMyFavoriteIds` paged-scan** is a stopgap. At >5000 favorites it'd stop. Backend follow-up: `GET /api/v1/users/me/favorite-ids` returning just the id set.

### Bug 6 caught by first real upload-modal exercise
- **Symptom:** `POST /api/v1/media/upload` returns 500. Stacktrace: `DataIntegrityViolationException: null value in column "s3_key" of relation "media_uploads" violates not-null constraint`.
- **Root cause (backend Slice 2, latent):** `UploadService.create()` called `uploadRepository.saveAndFlush(upload)` *before* setting `s3_key`, then computed the key from the generated id, then `save()` again. The first flush violates the `NOT NULL` constraint. The two-phase design was meant to "use the generated id to build the key", but Postgres rejects the first INSERT.
- **Why it never surfaced earlier:** Slice 2's manual smoke test was the only exercise — and looking back, it likely never hit `/upload` because the test was via curl + Swagger and the user just tested response shapes, not full happy paths. The web Upload modal is the first end-to-end consumer.
- **Fix:** generate the UUID up-front in the service (`UUID.randomUUID()`), set both `id` and `s3_key` on the entity, save once. Spring Data does a SELECT-then-INSERT (merge path because id is non-null), but functionally correct. A future micro-opt would be implementing `Persistable<UUID>` on `MediaUpload` to skip the SELECT.
- **Lesson:** another bug Testcontainers integration tests would have caught at `./gradlew build` time. The "we'll add e2e/integration tests later" deferral keeps cashing in real-world bugs.

### Bug 7 caught immediately after Bug 6 fix — transcode-dispatch race
- **Symptom:** `TranscodeWorker` throws `IllegalStateException: Illegal upload state transition: AWAITING_UPLOAD → PROCESSING`.
- **Root cause:** `UploadService.markUploaded` is `@Transactional` and publishes to RabbitMQ *inside* the transaction (after the entity save, before commit). RabbitMQ delivers the message synchronously enough that the worker — same JVM, separate thread + tx — pulls it and reads the upload row before the original transaction has committed. The worker sees stale `AWAITING_UPLOAD` state, tries to transition to `PROCESSING`, and the state machine (correctly) rejects it.
- **Fix:** wrap the dispatch in a `TransactionSynchronizationManager.registerSynchronization(...)` with `afterCommit()`. Spring fires the callback only after the JDBC commit succeeds, so the worker sees the committed `UPLOADED` state and the transition is legal.
- **Architectural note:** this is the textbook "publish after commit" pattern. We deliberately chose NOT to put transcode dispatch through the outbox (architecture.md, Section 5.3 — "RabbitMQ direct path"), so the commit boundary was implicit and easy to get wrong. If we add more "publish to external system from a service method" sites, generalising via Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` would be cleaner than ad-hoc registrations.
- **Lesson (running tally):** Testcontainers + RabbitMQ test would have caught Bugs 6 *and* 7. Both Slice 2 latent bugs.

### Bug 8 caught next message-cycle — FFmpeg encoder unavailable
- **Symptom:** `TranscodeWorker` throws `IOException: Process ffmpeg exited 8 ... Unknown encoder 'libwebp_anim' ... Encoder not found`. The whole job fails after the WebP step.
- **Root cause:** Homebrew's default `ffmpeg` formula no longer ships with `libwebp` enabled — the configure line shows `--enable-libx264 --enable-libvpx --enable-libx265` etc but not `--enable-libwebp`. Slice 2 documented `libwebp_anim` as one of the 4 renditions, but never verified the host binary supported it.
- **Architecture mismatch:** the original design treated transcode as all-or-nothing — one encoder failure aborted the whole job. That's the wrong tradeoff for missing optional encoders.
- **Fix:** wrap each rendition in a `tryRendition(...)` helper. Per-encoder failures log a warning and skip; the other renditions still get produced + saved + uploaded. `markReady()` writes whatever subset succeeded. `MediaCard` already falls back webp → gif → poster, so missing WebP is invisible to the user. Only if *all* renditions fail does the job error out (which routes to DLQ).
- **Lesson:** transcoder pipelines should default to "best-effort per output, fail only if everything failed". Future renditions (HD, vertical, etc.) inherit this resilience for free.

### Bug 9 caught when viewing uploaded media on channel page — MinIO returns 403
- **Symptom:** upload succeeds, redirect lands on `/channels/[username]`, the row appears in the API response, but `<img>` shows broken-image icon. `curl` of the rendition URL: `HTTP 403`.
- **Root cause (backend Slice 2, latent):** the bucket created by `BucketBootstrapper` has the default-private MinIO policy. Renditions are CDN-style public content (anyone with the URL should be able to GET them), but the bucket policy was never set to allow that. Originals stay private (correct); renditions were never opened.
- **Fix:** added a `putBucketPolicy` call in `BucketBootstrapper.run()` that allows `s3:GetObject` from `*` on the `renditions/*` prefix only. Originals (`uploads/*`) remain private — only the uploader-tagged backend code can presign reads on them.
- **Recovery:** applied the same policy out-of-band via `mc anonymous set-json` so existing renditions become visible without a backend restart. Next restart re-applies it idempotently.
- **Production note:** real envs would front the bucket with a CDN (CloudFront / Cloudflare) and apply the same prefix policy on the origin. CDN caches eagerly; origin reads are 1-per-rendition.

### Cumulative latent-bug count for backend Slice 2 (upload + transcode + storage)
Bugs 6 (s3_key NOT NULL), 7 (tx-commit race), 8 (libwebp not installed), 9 (bucket policy) — **all four latent since Slice 2**, all surfaced in <30 minutes once the web upload modal actually exercised the full pipeline. The strongest argument for the deferred Testcontainers-based integration tests so far.
