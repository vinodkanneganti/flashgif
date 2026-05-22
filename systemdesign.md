# FlashGif — System Design Document

**Owner:** Platform Engineering
**Status:** Active — backend v1 in production-shape, frontend pending
**Authors:** Staff Software Architect, Platform team
**Companions:** [architecture.md](architecture.md) (implementation reference) · [progress.md](progress.md) (decision log)

---

## 1. Executive Summary & Design Goals

### 1.1 High-Level Overview

FlashGif is a global GIF and short-video discovery, hosting, and sharing platform. End users search and save GIFs through a public-facing web and mobile experience; creators upload originals through a direct-to-object-storage pipeline that produces multiple transcoded renditions; third-party developers integrate GIF search into their own products via authenticated API keys with per-key rate limiting and usage analytics.

The system is intentionally a **modular monolith on day one** — package-by-feature boundaries inside a single Spring Boot deployable — with clean extraction points to a service-oriented topology when load justifies it. Read traffic dominates (≈100:1 vs writes), so the design optimises for cheap, cacheable, denormalised reads with a transactional outbox bridging the relational source of truth to the search index.

### 1.2 Core Non-Functional Requirements (NFRs)

**Scalability targets (v1 ambition, 12-month horizon)**

| Dimension | Target | Notes |
|---|---|---|
| DAU | 10M | Daily active end users |
| Concurrent connections | 100k peak | 99% reads, mostly search/trending |
| Search QPS | 10k peak / 2k sustained | Heavily cacheable |
| Upload throughput | 1k concurrent uploads, ~10/s sustained | Bound by FFmpeg + S3 |
| Developer API | 100k registered keys, 60 req/min default tier | Pluggable per-key quotas |
| Media catalog | 100M items, growing 1M/month | Postgres + ES |
| Object storage | ~1 PB renditions over 24 months | S3 with CDN egress |

**Availability targets**

| Path | SLO |
|---|---|
| Public read (search, trending, channel, suggestions) | 99.95% |
| User-authenticated read/write (favorites, profile, /me) | 99.9% |
| Upload presign + status | 99.9% |
| Transcode pipeline (async — measured by P99 turnaround) | 99% within 30s |
| Developer API | 99.95% (contractual; mirrors public read) |
| Auth (login, refresh) | 99.95% |

**Latency bounds**

| Endpoint class | p95 | p99 |
|---|---|---|
| Trending (cached) | 50ms | 100ms |
| Search (warm path) | 200ms | 500ms |
| Suggestions (cached prefix) | 30ms | 80ms |
| Channel profile | 100ms | 250ms |
| Upload presign | 100ms | 300ms |
| Login / refresh | 150ms | 400ms |
| Favorite / unfavorite | 100ms | 300ms |
| Developer search (same as public + rate-limit check) | 220ms | 550ms |

End-to-end transcode wall-clock is **not** a synchronous latency budget — it's measured as a queue-to-`READY` distribution: p50 ≤ 5s, p95 ≤ 30s, p99 ≤ 120s for inputs under 20 MB.

**Data consistency model**

| Data class | Model | Justification |
|---|---|---|
| Accounts, sessions, refresh tokens | **Strong** (single-row, single-region Postgres) | Auth correctness; lost-write here is a security event |
| Favorites, collections | **Strong** (per-row pessimistic lock for counter mutations) | Idempotent UX; double-counting is visible to the user |
| Media metadata, uploads | **Strong** in Postgres | Audit trail; refund/dispute resolution |
| Search index | **Eventual** (outbox-driven, p95 lag ≤ 5s) | Search is read-mostly; a 2-3 second stale window is invisible to users |
| Trending cache | **Eventual** (60s TTL) | High-traffic, low-cardinality; freshness is not safety-critical |
| Suggestion cache | **Eventual** (5min TTL) | Typed-prefix space; cache hit rate dominates |
| Developer usage counters | **Eventual** (Redis INCR, 35-day TTL; nightly rollup later) | Billing-grade accuracy is not yet a requirement |
| Popularity score | **Eventual** (recomputed every 5 min in batch) | Avoids per-favorite write amplification |

We trade strong consistency only where we control the staleness window and where the application semantics absorb it. Everything else is strong.

---

## 2. High-Level Architecture & Component Decomposition

### 2.1 Topology

```
                                ┌─────────────────────────────────┐
                                │           CDN (CloudFront)      │
                                │  static renditions + /static/*  │
                                └─────────────┬───────────────────┘
                                              │ origin pull (cache-miss)
                                              ▼
                                     ┌────────────────────┐
                                     │  Object Storage    │
                                     │  S3 (uploads/,     │
                                     │  renditions/)      │
                                     └────────────────────┘
                                              ▲
                                              │ presigned PUT
            ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
            │  Web SPA    │ │  iOS App    │ │  Android App│  (Next.js, Swift, Kotlin)
            └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
                   │               │               │
                   │ TLS + JWT     │               │
                   ▼               ▼               ▼
            ┌──────────────────────────────────────────────────────┐
            │              Application Load Balancer (TLS term.)   │
            └─────────────────────┬────────────────────────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────────┐
        │                         │                             │
        ▼                         ▼                             ▼
┌──────────────────┐    ┌──────────────────┐         ┌──────────────────┐
│  API Pod (N)     │    │  API Pod (N)     │  ...    │  API Pod (N)     │
│  Spring Boot     │    │  Spring Boot     │         │  Spring Boot     │
│  stateless       │    │  stateless       │         │  stateless       │
│  HPA-scaled      │    │  HPA-scaled      │         │  HPA-scaled      │
└────────┬─────────┘    └────────┬─────────┘         └────────┬─────────┘
         │                       │                            │
         └───────────┬───────────┴────────────┬───────────────┘
                     │                        │
   ┌─────────────────┼─────────────┬──────────┼─────────────────┐
   │                 │             │          │                 │
   ▼                 ▼             ▼          ▼                 ▼
┌──────────┐  ┌───────────┐  ┌─────────┐ ┌──────────┐  ┌────────────────┐
│Postgres  │  │Elastic-   │  │ Redis   │ │ RabbitMQ │  │ Worker Pod (M) │
│primary + │  │search     │  │ Cluster │ │ Cluster  │  │ (Spring Boot,  │
│replicas  │  │cluster    │  │ (cache  │ │ (jobs)   │  │  worker profile│
│          │  │(media     │  │  +rate  │ │          │  │  consumer-only)│
│          │  │ index)    │  │  +ctrs) │ │          │  │  HPA on queue  │
│          │  │           │  │         │ │          │  │  depth         │
└──────────┘  └───────────┘  └─────────┘ └──────────┘  └───────┬────────┘
                                                                │
                                                                ▼
                                                       ┌──────────────────┐
                                                       │  FFmpeg (in pod) │
                                                       │  → S3 renditions │
                                                       └──────────────────┘
```

### 2.2 Component decomposition

| Component | Responsibilities | State | Scaling axis |
|---|---|---|---|
| **CDN** (CloudFront / Cloudflare) | Cache rendition deliveries (`renditions/*`); absorb 95%+ of public read bandwidth | Stateless edge cache | Provider-managed; pop-level horizontal |
| **Load Balancer** (ALB / equivalent) | TLS termination, health checking, sticky-session **not** required (stateless API) | Stateless | Provider-managed |
| **API Pod** (Spring Boot, default profile minus Rabbit listeners) | Handle all HTTP: search, auth, favorites, channels, upload orchestration, dev API. **No long-running work.** | **Stateless** | Horizontal via HPA on CPU + p95 latency; 5–50 pods typical |
| **Worker Pod** (Spring Boot, `worker` profile) | Consume `media.transcode` queue → run FFmpeg → upload renditions → update Postgres | **Stateless** (per job) | Horizontal via HPA on RabbitMQ queue depth; 2–20 pods typical; CPU-bound so prefetch=1 |
| **Outbox Poller** (in-process, scheduled task on API pods) | Drain `outbox_events` → Elasticsearch | Stateless (locks via DB query) | Implicit with API pods; one runs at a time per pod, work is partitioned by row claiming |
| **Popularity Recompute Job** (in-process, scheduled task) | Every 5 min recompute `popularity` for recently-changed media; emit outbox events | Stateless | Single-leader concern: use ShedLock on Redis to ensure only one pod runs it |
| **PostgreSQL** | Source of truth: users, media, uploads, favorites, collections, keys, outbox | **Stateful** | Vertical scaling first (handles target QPS easily); read replicas for analytics later; partition `outbox_events` by `created_at` once growth requires |
| **Elasticsearch** | Denormalised media index; `multi_match` + `function_score` + `search_as_you_type` | **Stateful** | Horizontal: 3-node minimum for HA; shard `media_v1` by id hash; scale via index aliases (zero-downtime reindex) |
| **Redis** | (a) `@Cacheable` trending + suggestion entries (b) per-key developer usage counters (c) future: rate-limit buckets, ShedLock locks | **Stateful** but recoverable | Single-shard with replica for v1; Cluster mode when memory or QPS exceeds one node |
| **RabbitMQ** | `media.transcode.requests` queue + DLQ; durable, single consumer group (the worker pool) | **Stateful** | Cluster mode (3 nodes, quorum queues) for HA; queue is shallow because transcode SLO keeps depth ≤ ~100 normally |
| **S3 / MinIO** | Binary object storage: `uploads/{uploadId}/{filename}` (originals, retained for re-transcode), `renditions/{uploadId}/{kind}.{ext}` | Stateful, managed | Provider-scaled; bucket policies + CORS managed via IaC |

### 2.3 Stateless vs stateful boundary

The **only** stateful components in the data path are Postgres, Elasticsearch, Redis, RabbitMQ, and S3 — all are external managed services in production. API and Worker pods are fully ephemeral: any pod can serve any request, any worker can pick up any job. This enables horizontal scaling, rolling restarts, and zone-failure tolerance without any session affinity machinery.

The two in-process scheduled tasks (`OutboxPoller`, `PopularityRecomputeJob`) appear to be a stateful concern but both are designed for safe over-execution:
- `OutboxPoller` uses `SELECT ... FOR UPDATE SKIP LOCKED` semantics (Postgres) when claiming events, so multiple pollers across pods will partition the work without coordination overhead.
- `PopularityRecomputeJob` is wrapped in a ShedLock distributed lock (Redis-backed) so only one pod runs the batch at a time; if it dies mid-batch, another pod takes over on the next interval.

---

## 3. Data Architecture & Storage Strategy

### 3.1 Data store selection per workload

| Workload | Store | Why this store |
|---|---|---|
| Accounts, sessions, audit | **PostgreSQL** | ACID, foreign keys, unique constraints, well-understood operational story. Auth correctness is non-negotiable. |
| Media metadata (title, tags, rating, status, counters, rendition URLs) | **PostgreSQL** | Same row also holds the JSONB `rendition_urls` for atomic single-fetch reads in non-search paths. |
| Favorites, collections, collection items | **PostgreSQL** | Tight relational integrity (FK cascades on user/media delete), composite PKs, no eventual consistency wanted. |
| Outbox events | **PostgreSQL** | Atomicity with the domain change is the whole point — pulling outbox into a separate store reintroduces the dual-write problem. |
| Searchable media projection | **Elasticsearch** | Typo tolerance (`fuzziness: AUTO`), `search_as_you_type` for autocomplete, blended ranking via `function_score`. Postgres FTS could do match + stem, but the autocomplete and ranking story is much weaker. |
| Trending list, autocomplete prefixes | **Redis (cache)** | High-read, low-cardinality, freshness window of seconds-to-minutes is acceptable. Sub-ms read path. |
| Developer usage counters (daily per-key totals) | **Redis (INCR)** | Atomic counter, TTL-based retention (35 days), no transactional cost. Roll-up to Postgres `developer_usage_daily` for long-term archive at scale. |
| Future: rate-limit token buckets | **Redis (Bucket4j-backed)** | Distributed atomic counters with TTL. Currently in-memory; swap is one class. |
| Binary objects (originals + renditions) | **S3** | Cheap, durable (11 9s), CDN-friendly, presigned-URL story matures the upload pipeline. |

### 3.2 Schema highlights

- **Outbox table** `outbox_events (id, aggregate_type, aggregate_id, event_type, payload jsonb, created_at, published_at)` with a partial index on `WHERE published_at IS NULL` so the poller's hot query is index-only.
- **Pessimistic lock for counters**: `media.favorite_count` mutated via `SELECT … FOR UPDATE` to prevent lost updates under concurrent favoriting.
- **Composite primary keys** on relationship tables (`favorites (user_id, media_id)`, `collection_items (collection_id, media_id)`) — natural keys, no surrogate id, dedup is enforced at the DB.
- **Hash-on-write for secrets**: `refresh_tokens.token_hash` and `developer_keys.key_hash` are SHA-256 bytea. The raw value is shown to the client exactly once at issuance; lookup is hash-comparison; revocation is a `revoked_at` timestamp.
- **Citext / inet avoided**: Postgres-specific column types created friction with Hibernate's strict schema validator (Slice 3 bug history). Default to portable `varchar` + application-layer normalization (lowercase email, IP string).

### 3.3 Caching strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer       │ Stored what                  │ TTL    │ Invalidation     │
├─────────────────────────────────────────────────────────────────────────┤
│ Browser/SDK  │ JWT access token             │ 15 min │ Token expiry     │
│              │ Static rendition assets      │ 1 yr   │ Versioned URLs   │
├─────────────────────────────────────────────────────────────────────────┤
│ CDN edge     │ /renditions/*                │ 1 yr   │ Cache-Control    │
│ (CloudFront) │ /static/*                    │ 1 yr   │ Versioned URLs   │
├─────────────────────────────────────────────────────────────────────────┤
│ Redis        │ trending:{type}              │ 60s    │ TTL only (no     │
│              │                              │        │ active invalid.) │
│              │ suggestions:{prefix.lower()} │ 5 min  │ TTL only         │
│              │ dev:usage:{key}:{yyyyMMdd}   │ 35 d   │ TTL only         │
├─────────────────────────────────────────────────────────────────────────┤
│ Postgres     │ Hibernate L1 (per-request)   │ Tx     │ Tx commit/rollback│
│              │ Connection pool (HikariCP)   │ —      │ —                │
└─────────────────────────────────────────────────────────────────────────┘
```

**Pattern: cache-aside via Spring `@Cacheable`.** Read flow: check Redis, on miss compute + populate. Write flow: services don't write through — they let the next read repopulate. This is correct for our cases because:

- **Trending** is computed, not authored; no write path to invalidate.
- **Suggestions** are derived from `title.suggest`; the only "write" is new media being indexed, which we accept will take up to 5 minutes to surface in autocomplete (eventual consistency is the design).

If we ever cache user-authored data (e.g., favorite count badges) we'll move to **write-through invalidation** — service writes data, then `cache.evict(key)`.

**Eviction policies:** all Redis caches use **TTL-only** in v1. We size the Redis instance for peak working set (trending: ~10 list entries per type × a few types ≈ kilobytes; suggestions: ~10k popular prefixes × 1KB each ≈ 10 MB; usage counters: 100k keys × 35 days × few bytes ≈ 100 MB). LRU eviction kicks in only as a backstop; if it does, we resize.

### 3.4 Search index lifecycle

The `media` alias points at one concrete index (`media_v1`). Schema evolution is **build-new-index + alias swap**:

1. Build `media_v2` with the new mapping (running app continues serving from `media_v1`).
2. Backfill: stream all published rows from Postgres → `media_v2` via the existing indexer code path with a "reindex mode" flag.
3. Atomic alias swap: `POST _aliases {actions: [{remove: media→v1}, {add: media→v2}]}` is a single ES operation.
4. Retire `media_v1` after a soak period (24h is generous).

Zero downtime for clients, zero application redeploy.

---

## 4. Data Flow & Communication Patterns

### 4.1 Communication patterns

| Pattern | Protocol / Tech | Used for |
|---|---|---|
| Synchronous request/response (external) | REST over HTTP/1.1 (HTTP/2-ready at the ALB) | All client-facing endpoints |
| Synchronous (internal) | None today (monolith) | Future inter-service: gRPC when we extract |
| Asynchronous fire-and-forget | RabbitMQ direct exchange + durable queue | Media transcode jobs (long-running, CPU-bound) |
| Asynchronous via transactional outbox | Postgres `outbox_events` → poller → ES | Search index sync, popularity recompute |
| Pull-mode scheduled batch | Spring `@Scheduled` with ShedLock | Popularity recompute, future usage rollup |

REST was chosen over GraphQL because (a) the access patterns are simple and CRUD-shaped, (b) Cloudflare-style CDN caching works trivially on GET URLs, (c) the OpenAPI contract gives us client codegen for free across web/iOS/Android. GraphQL becomes interesting when client query shapes vary widely — not the case here.

### 4.2 Primary user workflow: end-to-end upload + discovery

```
[1] Creator opens upload UI                                                              t=0s
       │
[2] Web SPA → POST /api/v1/media/upload {filename, contentType, size}                    t=0.1s
       │       (Authorization: Bearer <JWT>)
       │       UserJwtFilter validates JWT; SecurityContext = UserPrincipal
       │       UploadService:
       │         - INSERT media_uploads (status=AWAITING_UPLOAD, uploader_id=<userId>)
       │         - generate S3 presigned PUT (15-min expiry)
       │       returns {uploadId, presignedUrl, expiresAt}
       │
[3] Browser PUTs the file directly to S3                                                 t=2s
       │       (bypasses our backend — bandwidth doesn't traverse our infrastructure)
       │
[4] Web SPA → POST /api/v1/media/upload/{uploadId}/complete                              t=2.5s
       │       Backend HEAD s3://uploads/{uploadId}/{filename}  → 200
       │       UPDATE media_uploads status=UPLOADED
       │       PUBLISH to RabbitMQ exchange `media.transcode` (routing_key=transcode)
       │       returns 202 Accepted
       │
[5] RabbitMQ → Worker pod (one consumer wins the message)                                t=2.6s
       │       @RabbitListener handler:
       │         - UPDATE status=PROCESSING
       │         - download original from S3
       │         - ffprobe → width/height/duration
       │         - transcode 4 renditions (mp4, webp, gif, poster) via FFmpeg
       │         - upload renditions to s3://renditions/{uploadId}/
       │         - UPDATE status=READY, rendition_urls={mp4:..., webp:..., ...}
       │       on exception: UPDATE status=FAILED + re-throw → Rabbit redelivers
       │                     → DLQ after retry exhaustion
       │
[6] Web SPA polls GET /api/v1/media/status/{uploadId} every 2s                           t=3-10s
       │       sees status=READY, fetches rendition URLs
       │
[7] Web SPA → POST /api/v1/media/metadata {uploadId, title, tags, contentRating}         t=12s
       │       PublishService:
       │         - INSERT media (carry rendition_urls + uploader_id from upload row)
       │         - UPDATE media_uploads status=PUBLISHED
       │         - INSERT outbox_events ("media.published", {mediaId})  -- SAME TX
       │       returns 201 Created with media id
       │
[8] OutboxPoller (next ~2s tick on any API pod)                                          t=14s
       │       SELECT … WHERE published_at IS NULL LIMIT 100
       │       for each: MediaIndexer.upsert(mediaId)
       │                  - reload Media from Postgres
       │                  - project to MediaDocument (incl. uploader.username lookup)
       │                  - searchRepository.save(doc)  -- ES PUT _doc/{id}
       │       UPDATE outbox_events SET published_at = now()
       │
[9] Anyone searching for the new media                                                   t=15s
       │       GET /api/v1/search?q=<title>  → ES match → result includes new media
       │
   End-to-end: ≈15 seconds from upload-start to search-discoverability.
```

### 4.3 Critical-path latency budget for the search hot path

```
GET /api/v1/search?q=happy
  ALB TLS + routing                                ≈   5 ms
  Network LB → pod                                 ≈   2 ms
  Spring Security filter chain                     ≈   5 ms
  SearchController dispatch + validation           ≈   2 ms
  SearchService.search()
    NativeQuery build                              ≈   1 ms
    Elasticsearch RTT (HTTP) + query exec         ≈  50 ms  (search-warm)
    Hit projection (10 docs → DTO)                 ≈   2 ms
  JSON serialization                               ≈   3 ms
  ALB response + network back                      ≈   5 ms
                                              ─────────────
                                                  ≈  75 ms   typical
                                                  ≈ 200 ms   p95 budget
```

Trending hits Redis instead of ES, dropping the 50 ms ES round-trip to ~2 ms — that's why the cached endpoint's p95 budget is 4x tighter.

---

## 5. Resiliency, Fault Tolerance & Security

### 5.1 Failure modes & mitigations

**Database (Postgres) primary failure**
- *Detection:* Hikari pool exhaustion, JDBC connection refused, replication lag alarm
- *Mitigation:* Hot standby with automatic failover (RDS Multi-AZ or equivalent); typical failover ≤60s. During failover, API pods return 503 (Hikari fails fast); clients retry with exponential backoff.
- *Data loss:* Synchronous replication on the critical path (orders/auth-equivalent — we don't have orders yet); RPO = 0 for users + auth, ≤1s for media writes.

**Elasticsearch cluster yellow / red**
- *Detection:* Cluster health endpoint polled by health check; circuit on the API side trips at 5xx > 50% over 30s.
- *Mitigation:* Search endpoints degrade to **stale-allowed mode** by falling through to Postgres-FTS for `multi_match`-on-title only (loses ranking and suggestions). Trending falls back to a static daily snapshot in Redis. Suggestions return empty (UI hides the dropdown).
- *Recovery:* Once cluster is green, `OutboxPoller` is the catch-up mechanism — any writes that landed in Postgres during the outage still have `published_at = NULL` and will be drained on the next tick.

**Redis failure**
- *Detection:* connection refused / timeout
- *Mitigation:* `@Cacheable` falls through to the underlying method (cache miss = compute fresh). Trending QPS spike against ES is the main concern; rate-limit the **per-pod** ES request rate so a Redis outage degrades trending latency but doesn't cascade into an ES outage.
- *Dev usage counters:* lost during outage. Acceptable; analytics report a gap. Future Postgres rollup gives us long-term durability.

**RabbitMQ outage**
- *Detection:* publish timeout
- *Mitigation:* Upload `/complete` endpoint returns 503; client can retry. The upload row stays in `UPLOADED` state and a sweeper (deferred) will re-publish on recovery. Existing in-flight transcode jobs are durable on the broker (persistent messages + quorum queues) and survive broker restart.
- *DLQ:* `media.transcode.dlq` collects poison messages after 3 redeliveries. Operations alarm on DLQ depth >0.

**Worker pool starvation / FFmpeg hang**
- *Mitigation:* Per-job timeout (configurable, default 120s) wrapping the `ProcessBuilder` invocation. Hard-kill the FFmpeg child process on timeout; mark `status=FAILED`; re-throw → DLQ.
- *Resource isolation:* Worker pods set `cpu.limits` = 2 cores per pod and `prefetch=1` so one slow job doesn't block its neighbor.

**S3 unavailability**
- *Mitigation:* Upload presign fails fast (503); transcode worker retries with backoff (3 attempts over 30s) before failing the job to DLQ. CDN absorbs read traffic for ≥1 hour from edge cache.
- *Cross-region replication:* future; v1 is single-region.

**Network partition between pods and data stores**
- *Mitigation:* Hikari + Lettuce + ES Rest client all have configurable connection / socket timeouts; default 3s connect, 10s socket. No infinite waits. Spring's exception translation surfaces these as 503 to the client.

**Cascading retries (retry storms)**
- *Mitigation:* Client SDKs (when we publish them) use **exponential backoff with full jitter**. Backend services that call downstream (only worker → S3 today) use the same pattern.
- *Future:* Resilience4j circuit breakers around ES and S3 calls once we have inter-service calls.

**Thundering herd on cache expiry**
- *Mitigation:* Today's trending TTL is 60s and the work is cheap; not a real risk at our QPS. At higher scale: per-key request coalescing (`@Cacheable(sync=true)`) so only one pod recomputes per expiry boundary.

### 5.2 Security

**Authentication**

Two parallel identity models, isolated by `SecurityFilterChain`:

| Audience | Credential | Storage | Filter | TTL |
|---|---|---|---|---|
| End users | JWT access (HS256, signed with server secret) | Stateless (signed claims) | `UserJwtFilter` | 15 min |
| End users | Opaque refresh token | SHA-256 hashed in `refresh_tokens` | n/a (used at `/auth/refresh`) | 30 days, rotating |
| Third-party developers | Opaque API key (`fg_<base64url>`) | SHA-256 hashed in `developer_keys` | `DeveloperApiKeyFilter` | Long-lived; revoke via DELETE |
| (future) Admin | Same JWT chain + role claim | Stateless | Same as users + `@PreAuthorize("hasRole('ADMIN')")` | 15 min |

**Authorization**

- Route-level: `requestMatchers(...).authenticated() / .permitAll() / .denyAll()` in `SecurityConfig`.
- Resource-level: services check ownership (e.g., collection owner) and return **404** (not 403) on ownership failure so existence of private resources doesn't leak.
- Method-level: `@PreAuthorize` reserved for future role-based checks (admin slice).

**Rate limiting**

- Anonymous public reads: bounded by the CDN; origin protected by ALB-level rate limit per IP (future).
- Per-key developer rate limit: in-memory token bucket today (60 req/min default), Redis-backed Bucket4j tomorrow for multi-instance. 429 + `Retry-After` on exhaustion. 429s don't count toward billable usage.
- Per-user auth: account-lockout after N failed `/auth/login` attempts (future; today: no protection — acknowledged risk).

**Data encryption**

| Layer | At rest | In transit |
|---|---|---|
| Postgres | AWS-RDS volume encryption (KMS) | TLS to database |
| Elasticsearch | EBS volume encryption | TLS to cluster |
| Redis | At-rest encryption (provider-managed) | TLS to cluster |
| RabbitMQ | At-rest encryption | TLS (amqps://) |
| S3 | SSE-S3 (or SSE-KMS for sensitive buckets) | TLS |
| Client ↔ ALB | n/a | TLS 1.2+ (managed cert via ACM) |
| Client ↔ S3 (direct PUT/GET) | n/a | TLS |

Secrets (JWT signing key, DB credentials, S3 keys) are sourced from a secrets manager (AWS Secrets Manager / Vault) at pod boot — never baked into images or committed.

**Password handling**

- BCrypt with default cost (10) for end-user passwords. `PasswordEncoder` interface lets us bump cost or migrate to Argon2id without API change.
- API keys + refresh tokens use SHA-256 (not BCrypt) because they're high-entropy (256-bit random) — brute-force protection comes from the entropy, not the hash cost. BCrypt-ing high-entropy keys gains nothing and costs O(100x) per lookup.

**Network isolation (production target)**

- API + worker pods in private subnets; only ALB has public ingress.
- Data stores (Postgres, ES, Redis, RabbitMQ) in private subnets, security groups restrict ingress to the pod CIDR.
- S3 endpoint: VPC endpoint (PrivateLink) so worker-to-S3 traffic doesn't traverse the internet.
- Outbound egress restricted to known SaaS dependencies (e.g., email, analytics).
- WAF in front of ALB: OWASP top-10 rules, rate-limit per IP, geo-restriction if needed.

**Audit trail**

- `refresh_tokens` records `user_agent` + `ip` + `created_at` + `last_used_at` + `revoked_at` per session — enables a session-listing UI and forensics on credential compromise.
- `developer_keys` similarly records `created_at`, `last_used_at`, `revoked_at`.
- Application-level access logging via Spring's `CommonsRequestLoggingFilter` (sampled in prod). Future: structured logs to a SIEM.

**Threats explicitly not addressed in v1**

- Brute-force protection on `/auth/login` (no account lockout) — assume external WAF rate limiting until we implement.
- 2FA — deferred.
- Email verification — deferred; accounts assumed verified at register.
- Content moderation (NSFW detection on uploads) — `content_rating` is creator-declared today.
- DDoS — relies entirely on CDN + ALB-level protection from provider.

---

## 6. Frontend Architecture

The web client is a Next.js 14 (App Router) application that consumes the public API contract. It is a separate deployable from the backend, designed to be hostable on any Node-capable runtime (Vercel, container, self-hosted node) and to keep the backend free of any rendering concerns.

### 6.1 Stack and rationale

| Concern | Choice | Why |
|---|---|---|
| Framework | **Next.js 14 App Router** | First-class server components for SEO-critical pages (trending, search, channels), route handlers for the auth proxy, streaming for pages that fetch above-the-fold data |
| Language | **TypeScript 5.6 strict** | Catches API-contract drift at compile time; required for safe `openapi-typescript` consumption |
| Styling | **Tailwind CSS** | Utility-first scales with team velocity, zero runtime, trivial to theme via CSS variables |
| Components | **shadcn/ui (copy-paste)** | We own the components in-repo; full customization without runtime dep on a UI library; CSS-variable themed |
| Server state | **TanStack Query v5** | Caching, refetch-on-focus, optimistic updates, infinite query — all standard patterns |
| Client state | **Zustand** | ~1KB; modal-open state, theme, ephemeral UI flags. Anything server-derived stays in React Query |
| Forms | **React Hook Form + Zod** | Schema-first validation; Zod schemas mirror backend constraints (email format, password ≥12, etc.) |
| API types | **openapi-typescript** | Generates `types.ts` from `docs/openapi.yaml`; no runtime client library |
| API client | **Hand-written `fetch` wrappers** | Two wrappers: `apiFetch` for public/direct calls, `authedFetch` for same-origin cookie-backed calls with 401-refresh interceptor |
| Charts | **recharts** | Single bar chart for usage analytics; ~50 KB |
| E2E tests | **Playwright** | Auto-starts dev server; cross-browser-capable; chromium-only in CI for speed |
| Package manager | **pnpm 11** | Faster, smaller `node_modules`, workspace-friendly |

### 6.2 File layout

```
web/
├── src/
│   ├── app/                           Next.js App Router root
│   │   ├── layout.tsx                 Server component: SSR-fetches /me, seeds React Query
│   │   ├── page.tsx                   Home (trending) — server component
│   │   ├── HomeClient.tsx             Client island for TypeChips + masonry hydration
│   │   ├── search/
│   │   │   ├── page.tsx               force-dynamic shell
│   │   │   └── SearchClient.tsx       Infinite scroll, URL-state filters
│   │   ├── login/page.tsx             Server component: redirect home if authed
│   │   ├── register/page.tsx
│   │   ├── favorites/page.tsx         Server-side auth gate
│   │   ├── collections/{,/[id]}/
│   │   ├── channels/[username]/       Public; server-fetches profile
│   │   ├── settings/profile/         Owner-only
│   │   ├── dev/{,/keys/new,/usage}/   Developer dashboard
│   │   └── api/                       Route Handlers (same-origin proxy)
│   │       ├── auth/{login,register,refresh,logout}/
│   │       ├── users/me/
│   │       ├── channels/profile/
│   │       ├── favorites{,/[mediaId]}/
│   │       ├── collections/{,/[id]{,/items{,/[mediaId]}}}/
│   │       ├── media/{upload{,/[uploadId]/complete},status/[uploadId],metadata}/
│   │       └── usage/analytics/
│   │
│   ├── components/
│   │   ├── auth/                      LoginForm, RegisterForm, UserMenu
│   │   ├── layout/                    Header, Footer
│   │   ├── search/                    SearchBar, TypeChips
│   │   ├── media/                     MediaCard, MasonryGrid
│   │   ├── upload/                    UploadButton, UploadModal (3-stage)
│   │   ├── favorites/                 MediaTilePlaceholder
│   │   └── ui/                        shadcn primitives (Button, Input)
│   │
│   └── lib/
│       ├── env.ts                     Typed env access (required-at-load)
│       ├── utils.ts                   cn() className merger
│       ├── api/
│       │   ├── client.ts              apiFetch (public, direct to Spring)
│       │   ├── authed.ts              authedFetch (proxy via Next, 401-retry)
│       │   ├── endpoints.ts           search/trending/suggestions
│       │   ├── auth.ts                login/register/logout + Me type
│       │   ├── media.ts               upload pipeline
│       │   ├── favorites.ts           favorites + collections CRUD
│       │   ├── channels.ts            channel reads + profile PATCH
│       │   ├── developer.ts           keys + usage
│       │   └── types.ts               openapi-typescript output (stub today)
│       ├── auth/
│       │   ├── cookies.ts             Cookie names + option helpers
│       │   ├── server.ts              SSR readAccessToken / getCurrentUser
│       │   ├── session.ts             set/clear cookies from AuthResponse
│       │   ├── proxy.ts               proxyToBackend() Route Handler helper
│       │   └── schemas.ts             Zod for login/register
│       ├── upload/schemas.ts          Zod + file validation
│       └── query/
│           ├── keys.ts                React Query key factory
│           ├── QueryProvider.tsx      Per-tab QueryClient with optional SSR seed
│           ├── hooks.ts               useTrending, useSearch, useSuggestions
│           ├── authHooks.ts           useMe + login/register/logout mutations
│           ├── favoritesHooks.ts      Optimistic favorite + collection hooks
│           └── devHooks.ts            useDevKeys + useDevUsage
└── tests/e2e/                         Playwright specs
```

### 6.3 Rendering strategy

| Page | Mode | Why |
|---|---|---|
| `/` (home) | **Server-rendered + hydrate** (`dynamic = "force-dynamic"`) | SEO; first paint shows trending |
| `/search` | **Force dynamic, client islands** | URL state drives the query; SEO value is minimal (search-result pages aren't typically indexed) |
| `/channels/[username]` | **SSR direct to Spring** | SEO; first paint shows the creator's banner + bio |
| `/login` `/register` | **Server gate + client form** | Server component redirects home if cookie already present; client form handles input |
| `/favorites` `/collections` `/dev/*` | **Server auth gate, client lists** | Auth check on the server (cookie read); content fetched client-side via React Query |
| `/settings/profile` | **SSR with form seed** | Server-fetches the current profile to populate the form's `defaultValues` |
| `/api/*` | **Route Handlers** (no UI) | Same-origin proxy to Spring; reads httpOnly cookies the browser can't access |

The root `layout.tsx` is a server component that:
1. Reads the cookie via `next/headers#cookies()`
2. Calls Spring `/users/me` directly (server-to-server, with the bearer token)
3. Passes the resulting `user` to `<Header user={user}>` and seeds React Query via `<QueryProvider seed={{ me: user }}>`

This eliminates the "Login → UserMenu flash" — the first paint already shows the correct state.

### 6.4 Token storage and refresh flow

The architecture decision (see ADR-007 in Appendix E) is **httpOnly cookies set by Next.js Route Handlers**, never JavaScript-readable.

```
Browser ──login form──► POST /api/auth/login
                            │
                            ▼
                Next.js Route Handler
                            │
                            ▼
                    POST <spring>/api/v1/auth/login
                            │
                            ▼  AuthResponse {access_token, refresh_token, ...}
                            │
            Set-Cookie: flashgif_access  (HttpOnly, Secure, SameSite=Lax, 15min)
            Set-Cookie: flashgif_refresh (HttpOnly, Secure, SameSite=Lax, 30d,
                                          Path=/api/auth)
```

Subsequent authed calls go through `authedFetch`, which targets same-origin Next.js routes:

```
authedFetch("/api/users/me")
    │
    ├── browser sends flashgif_access cookie automatically
    ├── Next.js Route Handler reads cookie, sets Authorization: Bearer <jwt>,
    │   forwards to Spring
    └── on 401 from any non-auth path:
            POST /api/auth/refresh (sends flashgif_refresh)
            ├── Route Handler forwards refresh → Spring rotates → new pair
            ├── on success: retry original request once
            └── on failure: clear cookies, caller redirects to /login
```

**What the browser never sees:** raw JWT, raw refresh token. XSS exfiltration of session tokens is structurally impossible.

### 6.5 API client patterns — `apiFetch` vs `authedFetch`

Two distinct paths for two distinct trust models:

| Function | Talks to | Auth | Used for |
|---|---|---|---|
| `apiFetch` | Spring directly (CORS-allowed) | None | Public reads: `/api/v1/search`, `/api/v1/trending`, `/api/v1/channels/{username}` (server-side too) |
| `authedFetch` | Next.js Route Handlers (same-origin) | Cookie-backed | Anything that needs the user's session: `/me`, upload, favorites, collections, profile edit, dev keys, usage analytics |

This split avoids the unnecessary same-origin hop for the ~90% of traffic that's public reads, while keeping authed calls XSS-safe.

### 6.6 React Query patterns

- **Key factory** (`lib/query/keys.ts`) centralizes all query keys so invalidation is type-safe and lookups are searchable.
- **Server seeding** for `useMe` — root layout passes `{ me: user }` to `QueryProvider`, which pre-fills the cache with `setQueryData(queryKeys.me(), user)`.
- **Infinite pagination** via `useInfiniteQuery` with `IntersectionObserver` sentinel in `SearchClient` and the channel feed.
- **Optimistic mutations** for favorites: `onMutate` snapshots the cache and updates it speculatively; `onError` rolls back; `onSettled` re-fetches the canonical state.
- **Debounced queries** for suggestions: 200ms debounce in `SearchBar`, then `useSuggestions(prefix)` with `enabled: prefix.length >= 2` and `staleTime: 5min`.
- **Polling** for upload status: `setTimeout`-based loop (rather than React Query's `refetchInterval`) so we can short-circuit on terminal states (`READY` / `FAILED`).

### 6.7 Forms

`react-hook-form` for state + submission, `zod` for validation. Schemas mirror backend constraints exactly so client-side errors don't surprise the backend:

```ts
// lib/auth/schemas.ts
const password = z.string().min(12, "At least 12 characters");
const username = z.string().regex(/^[a-zA-Z0-9_]{3,30}$/, ...);
```

Backend errors that slip past client validation (e.g., 409 username collision) are surfaced inline via `setError("root", { message })`.

### 6.8 Performance budget

Production build (Next.js 14, gzipped):

| Route | Page-specific | First Load JS |
|---|---|---|
| `/` | 597 B | 108 KB |
| `/search` | 1.26 KB | 108 KB |
| `/login` `/register` | 3.0 KB | 139 KB (RHF + Zod cost) |
| `/channels/[username]` | 2.0 KB | 111 KB |
| `/dev/usage` | 106 KB | **210 KB** (recharts) |

Shared chunks total 87 KB. Largest individual page (`/dev/usage` with recharts) is 210 KB — well under the Lighthouse "good" threshold (250 KB) and only on a low-traffic admin page.

---

## Appendices

### A. Evolution path (when to break the monolith)

The current modular monolith is right-sized for our current load and team. Triggers for extraction:

| Trigger | Likely extraction |
|---|---|
| Transcode pool needs >50 pods or different hardware (GPU) | Worker → standalone service, REST/gRPC API for status |
| Search load saturates API pod CPU on JSON serialization | Search → standalone read-replica with its own deploy cadence |
| Developer API traffic dominates and needs separate scaling | Developer API → standalone gateway with its own rate-limit / billing concerns |
| Cross-region multi-region active-active needed | Postgres multi-region (or migrate to a distributed DB), search via per-region ES + global outbox |

Until then, modular monolith wins on operational simplicity.

### B. Capacity headroom (back-of-envelope at target NFRs)

- **API pods:** 10 pods × 200 req/s/pod = 2000 RPS sustained → 5000 RPS peak with 50 pods. Fits the 10k search QPS target if 50%+ are cache hits at the CDN (they will be — trending is browsed by every anonymous visitor).
- **Postgres:** 100M media rows × ~1KB row ≈ 100 GB; with indexes ~200 GB. Comfortably fits db.r6g.2xlarge. Write QPS dominated by favorites + uploads, ≤500 WPS sustained.
- **Elasticsearch:** 100M docs × ~2KB doc ≈ 200 GB. 3-node 32 GB RAM cluster handles working set comfortably. Sharded by id hash, 5 shards per index.
- **Redis:** working set ≤500 MB; single r6g.large with replica.
- **RabbitMQ:** queue depth normally <100; provisioned for 100k message burst.

### C. Glossary

| Term | Meaning |
|---|---|
| **NFR** | Non-Functional Requirement |
| **SLO** | Service Level Objective (internal target) |
| **RPO** | Recovery Point Objective (acceptable data loss window) |
| **RTO** | Recovery Time Objective (acceptable restoration time) |
| **DLQ** | Dead Letter Queue (where poison messages land) |
| **Outbox pattern** | Transactional DB row that captures intent to publish an event; drained by a separate poller |
| **Stale-allowed mode** | Degraded read mode that serves cached / fallback data when the primary source is unavailable |
| **Stampede / thundering herd** | Many requests racing to recompute the same cache entry the moment it expires |

### D. Interview Questions & Reference Answers

A representative bank of questions a candidate could be asked about this system, with the answers we'd want to hear. Organised by topic.

---

#### D.1 Requirements, scoping, and high-level architecture

**Q1. Walk me through the system in 60 seconds.**
FlashGif is a Giphy-style platform with three audiences: anonymous users browsing trending/searching, logged-in users uploading and curating favorites/collections, and third-party developers integrating GIF search via API keys. The backend is a Spring Boot modular monolith with five backing stores (Postgres for truth, Elasticsearch for search, Redis for cache + counters, RabbitMQ for the transcode queue, S3 for binaries). Reads dominate ~100:1, so we optimise for cacheable, denormalised reads with a transactional outbox bridging Postgres to ES.

**Q2. Why a modular monolith instead of microservices?**
Three reasons. (1) Team size: one team can ship a monolith faster than they can ship 6 services with their attendant deploy pipelines, schema-evolution coordination, and distributed-tracing infrastructure. (2) Domain stability: we're discovering the boundaries; microservices freeze them prematurely. (3) Operational tax: each service adds a deploy target, a runbook, a dashboard, and a 3am page. We pay that tax only when a module's scaling profile diverges from the rest. Modular packages with strict dependency rules give us the extraction option without the day-one cost.

**Q3. When would you break it up?**
Concrete triggers: transcode workers need GPUs (extract to a service), search saturates API pod CPU on serialization (extract read-replica), developer API needs separate billing/rate-limit infra (extract gateway), or we need multi-region active-active (extraction is a forcing function). Until those triggers fire, the cost of staying monolithic is lower than the cost of going distributed.

**Q4. Why not serverless (Lambda / Cloud Run)?**
Cold starts hurt our p95 latency budget (200ms for search) — JVM cold start alone exceeds that. FFmpeg transcoding doesn't fit Lambda's 15-min limit cleanly for some workloads, and cost-per-invocation gets worse than reserved compute past a few thousand QPS. Serverless wins for spiky, low-base-load workloads; we have sustained read traffic.

**Q5. Read/write ratio? Where does that show up in the design?**
~100:1 reads to writes (most users browse, few upload). Drives: aggressive caching (trending in Redis), CDN-fronted renditions, eventual consistency for search (writes propagate via outbox), expensive ES-indexing on the write path (we pay it once, amortise over many reads).

---

#### D.2 Data modeling and storage

**Q6. Why Postgres, not MongoDB?**
Strong relational integrity matters here (users → uploads → media → favorites with FK cascades), our access patterns are mostly indexed lookups, and JSONB gives us schemaless escape hatches (`rendition_urls`, `social_links`) without giving up ACID. Mongo would force us to choose between manual integrity (lost) and multi-document transactions (added complexity with worse isolation). Postgres also handles our scale (100M rows) on a single primary comfortably.

**Q7. Why Elasticsearch on top of Postgres? Why not just Postgres FTS?**
Postgres FTS handles match-and-stem reasonably. What it doesn't do well: typo tolerance (fuzziness), `search_as_you_type` autocomplete with n-gram subfields, and blended relevance + popularity ranking via `function_score`. Maintaining a separate ES is the cost; not having it means rebuilding all three features ourselves on a less-mature engine.

**Q8. How do you keep ES in sync with Postgres?**
Transactional outbox. Every domain change writes a row to `outbox_events` in the same DB transaction as the entity change — atomic, no dual-write. A scheduled poller drains the outbox and writes to ES. ES upserts are idempotent (key = mediaId), so at-least-once delivery is safe. If the poller crashes mid-batch, unprocessed rows still have `published_at IS NULL` and get retried.

**Q9. Why outbox and not Debezium / CDC?**
Outbox is simpler to operate (no Kafka Connect, no Debezium connector to monitor), gives us explicit control over event shapes (the row stores the payload we want, not the raw column diff), and is portable across DBs. CDC is the right answer at much larger scale where the application-level publish becomes a bottleneck, or when many consumers want the same event stream.

**Q10. Why two tables for the upload pipeline (`media_uploads` + `media`)?**
`media_uploads` is the state machine (`AWAITING → UPLOADED → PROCESSING → READY → PUBLISHED` with a `FAILED` terminal). `media` is the published, searchable entity. Keeping them separate means transcode failures or abandoned uploads never pollute the searchable corpus, and the `media` table's row shape is stable for indexing without conditional logic.

**Q11. Why composite primary keys on `favorites` and `collection_items`?**
The relationship itself is the identity — (userId, mediaId) is naturally unique and there's no surrogate id we'd ever URL-reference. The composite PK enforces dedup at the DB (no extra `UNIQUE` constraint needed), and queries by either column use the same index for free in Postgres.

**Q12. Why JSONB for `rendition_urls` and `social_links`?**
They're write-once, read-as-a-blob fields with no predicates on inner keys. A normalized child table (`rendition`, `social_link`) would mean a join on every read for zero query benefit. JSONB gives us atomic read with the parent row and tolerates field additions without migrations.

**Q13. How do you handle multi-tenant data isolation?**
We don't have multi-tenancy yet — every user is in one global namespace. If we add B2B tenants, we'd add a `tenant_id` column to every tenant-owned table, enforce via RLS (Postgres row-level security) or service-layer filtering, and shard or partition by `tenant_id` once any tenant gets large.

---

#### D.3 Search deep-dive

**Q14. Walk through a search query end-to-end.**
Client GETs `/api/v1/search?q=happy`. ALB routes to an API pod. Spring Security's filter chain identifies the request as public (no auth needed). `SearchController` calls `SearchService.search()`, which builds a `bool` query (`must: multi_match(q, fuzziness=AUTO) filter: status=published`) and wraps it in a `function_score` for popularity-weighted ranking when sort=relevance. The query goes to ES via the Java client. Hits are projected from `MediaDocument` to `MediaSummary` DTO and returned. End-to-end p95 ≈ 200ms.

**Q15. How does the popularity ranking work?**
`function_score` with `field_value_factor` on a precomputed `popularity` float on each ES doc. Popularity is `log(1 + favorite_count*3 + view_count) * exp(-age_days/7)` — favorites weighted 3× views, 7-day exponential decay. Recomputed every 5 minutes in a batch job (avoids per-favorite write amplification) and republished via outbox.

**Q16. How does autocomplete work?**
ES's `search_as_you_type` field type on `title.suggest`. It auto-generates n-gram subfields (`_2gram`, `_3gram`) under the hood. We query with a `multi_match` of type `bool_prefix` across the base field and its subfields. Results are deduped by title in-app. Cached in Redis per lowercased prefix with 5-minute TTL.

**Q17. How do you handle typos?**
`fuzziness: AUTO` on the `multi_match`. ES applies Levenshtein-distance fuzziness sized to term length (0 for 1-2 chars, 1 for 3-5, 2 for 6+). For real misspellings beyond edit distance 2, we'd add a query expansion using common-typo dictionaries or learn from search-then-click logs (deferred).

**Q18. How would you add semantic search?**
Add a `dense_vector` field to the ES mapping, generate embeddings via a small text-embedding model (e.g., sentence-transformers running in the worker, or a hosted API), store on index, and combine with the lexical query using RRF (Reciprocal Rank Fusion). ES 8.x supports this natively. Bigger lift: the embedding pipeline integrated into the outbox path. We deliberately deferred this — lexical search is good enough for hand-tagged GIFs.

**Q19. How do you handle deep pagination?**
We cap at page 50 today using `from + size`. Deeper pagination would switch to `search_after` with a tiebreaker on `id`. ES (and most search engines) get expensive past page 50-100 because the coordinator has to merge `from + size` results from every shard — bounded queries are a design contract, not a bug.

**Q20. How do you avoid stale search results when popularity changes?**
We accept staleness. The popularity recompute job runs every 5 min and emits outbox events only for media whose score changed by >0.01 (skip-tiny-deltas). For a user-visible counter like `favorite_count`, the Postgres row updates synchronously and the ES doc lags by up to 5 minutes — search ranking is a soft requirement, real-time counters aren't promised.

**Q21. How would you support per-user personalised search?**
Either (a) rerank ES results in the API layer based on user signals (favorited tags, followed creators), keeping ES results global — simplest, no ES-side cost, but limits how much personalisation is possible; or (b) add a learning-to-rank model with ES's LTR plugin, training offline on click data — more powerful but a significant ML infrastructure investment. We'd start with (a).

---

#### D.4 Caching

**Q22. What do you cache, where, and for how long?**
CDN: rendition binaries (1 year, versioned URLs). Redis: trending lists (60s TTL), suggestion results per prefix (5-min TTL), developer usage counters (35-day TTL). Browser: JWT access token (15 min, in memory or httpOnly cookie). Each TTL is sized to the freshness contract that endpoint promises.

**Q23. Write-through or cache-aside?**
Cache-aside for the read caches (trending, suggestions) because they're computed projections, not authored data — there's nothing to "write through." On read, check Redis; on miss, compute + populate. If we ever cache user-authored fields (e.g., favorite count badges), we'd switch to evict-on-write.

**Q24. How do you handle cache invalidation?**
Trending and suggestions: TTL only, no active invalidation. The freshness contract is "data is at most 60s / 5min stale" and that's by design. If we needed sub-second freshness we'd publish invalidation events from the writer path (extra coupling we don't want here).

**Q25. How do you prevent thundering herd on cache expiry?**
At our current QPS, the cost of N pods racing to recompute trending on expiry boundary is acceptable. At higher scale: `@Cacheable(sync = true)` so only one thread per pod per key recomputes (per-pod coalescing), or cluster-wide single-flight via a Redis lock (`SET NX EX`) with stale-while-revalidate fallback.

**Q26. How do you handle a hot key (one media goes viral)?**
We don't cache by mediaId today, so the hotspot lives in ES (which shards by id hash and handles it). Read hotspots on `/channels/{username}` could materialise if a creator went viral — we'd add per-username caching with short TTL. Write hotspot on `media.favorite_count` for one viral item is mitigated by the pessimistic lock + the fact that contention is bounded by user click rate.

**Q27. What's the failure mode if Redis dies?**
`@Cacheable` falls through to the underlying method (cache miss). Trending and suggestion QPS would spike against ES. We size ES to handle this — but to be safe, we'd add a circuit breaker on the ES client that sheds load past a threshold. Developer usage counters lose live data during the outage (analytics report a gap); long-term archive in Postgres (deferred) would recover this.

---

#### D.5 Upload pipeline

**Q28. Why presigned URLs instead of proxying through the backend?**
Bandwidth, scale, and cost. A 50MB GIF uploaded via the backend ties up an API pod's connection + bandwidth for the upload duration. With presigned URLs, the bytes never touch our infrastructure — browser PUTs directly to S3. Our backend issues the URL (cheap), checks completion via HEAD (cheap), enqueues transcode (cheap). API pods serve 100× more uploads with the same hardware.

**Q29. What if the user uploads to S3 but never calls `/complete`?**
The `media_uploads` row stays in `AWAITING_UPLOAD` forever. The S3 object exists, costing storage. We have a sweeper deferred: a daily job that finds `AWAITING_UPLOAD` rows older than 24h, HEADs S3 to see if the upload actually completed (race condition guard), and either marks complete or deletes the row + S3 object.

**Q30. Why a `/complete` callback instead of S3 event notifications?**
Three reasons. (1) Explicit flow is easier to reason about and test. (2) S3 events are async — the worker would fire before the row update could possibly settle. (3) We don't trust the network: if S3 events drop, uploads silently stall. With `/complete` the client knows and can retry.

**Q31. How do you handle transcode failures?**
The `@RabbitListener` handler wraps FFmpeg in try/catch. On exception: mark `status=FAILED`, re-throw. Rabbit redelivers up to N times (configured retry policy). After exhaustion, the message goes to `media.transcode.dlq`. We alarm on DLQ depth > 0. Manual replay path: an admin endpoint (deferred) can re-publish from DLQ after the underlying issue is fixed.

**Q32. How would you handle 10GB video uploads?**
Multipart upload (S3 supports this natively in the presigned-URL flow), chunked client with retry-per-chunk. Backend issues a multipart upload init + per-part presigned URLs. On complete, backend issues `CompleteMultipartUpload`. Transcode becomes more interesting — likely segment-transcode in parallel and concatenate. Out of scope for v1 (we cap at 100 MB).

**Q33. Idempotency of the upload flow?**
The `/upload` endpoint is not idempotent on retry (each call creates a new `uploadId`). We could add an `Idempotency-Key` header pattern (Stripe-style) where the same key returns the same uploadId. `/complete` and `/metadata` are idempotent — re-calling them transitions only forward through the state machine, never backward.

---

#### D.6 Async / messaging

**Q34. Why RabbitMQ and not Kafka?**
For a single producer → single consumer queue with at-most ~10/s throughput, RabbitMQ is operationally simpler. Kafka shines when you need (a) high throughput (100k+/s), (b) replay / event sourcing, (c) many consumer groups on the same topic. We have none of those today. If we add an event-streaming use case (e.g., user activity → analytics + recommendations + notifications), Kafka becomes interesting.

**Q35. At-least-once vs exactly-once delivery?**
At-least-once. The transcode worker is idempotent (re-transcoding produces the same rendition URLs; ES upserts are id-keyed). Exactly-once requires a transactional consumer model that's expensive and brittle; idempotent at-least-once is the standard pragmatic choice.

**Q36. What's in your DLQ strategy?**
A single shared DLQ per source queue (`media.transcode.dlq`). Messages land here after Rabbit's redelivery count is exceeded. We have monitoring on DLQ depth (alarm at >0 sustained, page at >100). Replay is an admin operation (deferred); typical workflow is: investigate root cause, fix, re-publish messages back to the source queue.

**Q37. How do you handle backpressure?**
Worker consumers use `prefetch=1` (one message in flight per consumer at a time). When workers are saturated, the queue grows — RabbitMQ tolerates this up to memory/disk limits. HPA scales worker pods based on queue depth (target depth ~50). If publisher rate sustainedly exceeds total worker throughput, we accept transcode latency growth as a signal to scale workers further or rate-limit uploads.

---

#### D.7 Authentication and security

**Q38. Why JWT for access tokens and opaque random for refresh?**
Access tokens are validated on every request — JWT means zero DB hit (signature verification only). Refresh tokens are validated rarely — opaque + DB-lookup gives trivial revocation (just delete the row), which is impossible with JWTs without a separate blacklist that defeats their stateless benefit. Mixed-mode is intentional.

**Q39. How do you revoke a JWT?**
You can't, mid-token. The 15-min TTL bounds the revocation lag — that's the design tradeoff. For account compromise: invalidate all refresh tokens (so re-login is required), force token rotation. If we ever need immediate revocation (e.g., admin-banned user), add a deny-list cache in Redis checked by `UserJwtFilter` keyed on jti; size is bounded by N revocations × TTL.

**Q40. Why hash refresh tokens with SHA-256 but passwords with BCrypt?**
Entropy. Refresh tokens are 256-bit random — brute-forcing the preimage is computationally infeasible regardless of hash cost. BCrypt's value is slowing down brute-force of low-entropy human-chosen passwords. Using BCrypt on a high-entropy random token adds ~100ms per auth check for zero security benefit.

**Q41. How are the two auth chains isolated?**
Two `SecurityFilterChain` beans with `@Order` and `securityMatcher`. `@Order(1) developerChain` matches `/api/v1/developer/**` only — uses `DeveloperApiKeyFilter` + `DeveloperRateLimitFilter`. `@Order(2) userChain` is catch-all — uses `UserJwtFilter`. Spring Security routes the request to the first chain whose matcher matches. Distinct rate limits, distinct filters, distinct entry points.

**Q42. Why 404 not 403 for ownership failures?**
Information leak. If we returned 403 for "exists but you can't see it" and 404 for "doesn't exist", an attacker can enumerate IDs to learn which UUIDs exist. Returning 404 uniformly hides existence. This matters most for private collections and developer keys.

**Q43. How do you protect against brute-force on login?**
Honest answer for v1: we don't. We rely on external WAF rate limiting per IP. Production-grade fix is per-account exponential backoff (1s, 2s, 4s, ..., capped) tracked in Redis, plus CAPTCHA after N failures. Account lockout is a denial-of-service vector (an attacker can lock anyone out by failing to log in as them), so prefer slowdown over lockout.

**Q44. How do API keys work end-to-end?**
Issue: server generates a 256-bit random, prefixes with `fg_`, returns to user once, persists only the SHA-256 hash. Authenticate: client sends `Authorization: Bearer <key>`; filter hashes, looks up in `developer_keys`, validates `status=active`, populates `DeveloperPrincipal`. Rate limit: per-key in-memory token bucket (60 req/min), 429 + `Retry-After` on exhaustion. Usage: Redis INCR per request (35-day TTL). Revoke: set `revoked_at`, status flips to `revoked`, subsequent requests 401.

**Q45. What's the worst-case data exposure if our DB leaks?**
Email + display name + bio + profile fields would leak (PII). Passwords are BCrypt — slow to crack, but possible for weak passwords. Refresh tokens and API keys are SHA-256 — useless to attacker without the raw token (effectively un-crackable for 256-bit randomness). Media metadata + favorite history are visible. No payment data (we have none). Mitigation: encrypt PII columns at-rest with field-level KMS keys for high-value fields like email (deferred).

---

#### D.8 Resilience and fault tolerance

**Q46. What happens if Postgres goes down?**
Hot standby with automatic failover (RDS Multi-AZ) — typical failover <60s. During failover, Hikari pool fails fast → API returns 503 → clients retry with backoff. ES + Redis remain available, so cached endpoints (trending, suggestions) and CDN-served renditions continue serving. Outbox poller resumes from where it left off — no data loss.

**Q47. What if Elasticsearch goes down?**
Critical reads degrade. Strategy: circuit-break the ES client at 50%+ 5xx over 30s, then fall back to "stale-allowed mode": trending reads from a static daily snapshot in Redis, search falls back to Postgres FTS for `title` matching only (loses ranking and autocomplete), suggestions return empty. UX degrades but stays up. Once ES is green, outbox poller catches up automatically.

**Q48. What about cascading failures?**
Bulkheads: separate Hikari pools per data store, separate ES + Redis client connection pools, separate thread pool for async operations (`@Async`). One subsystem getting slow doesn't starve threads serving others. Timeouts everywhere (3s connect, 10s socket default) so nothing waits forever. Circuit breakers on external calls (planned: Resilience4j) prevent retry storms.

**Q49. How do you handle network partitions between regions?**
Single-region today, so N/A. For multi-region active-active: Postgres becomes a global distributed DB (Aurora Global, Spanner, CockroachDB) or we accept per-region writes with conflict resolution. ES needs cross-cluster replication. Redis becomes per-region (data is regenerable cache). RabbitMQ becomes per-region (jobs are local). Outbox needs careful design for cross-region eventual consistency — likely shifts to CDC at that point.

**Q50. What's your RPO / RTO?**
Postgres: RPO ≤1s with synchronous standby; RTO ≤60s automatic failover. ES: RPO = the outbox lag (≤5s); RTO = cluster recovery time, fully repopulatable from outbox. Redis: RPO = unrestricted (cache, regenerable); RTO = ~30s for replica promotion. S3: 11 9s durability, RPO = 0 with cross-region replication.

---

#### D.9 Scaling

**Q51. How does this scale from 10k to 100M users?**
Read tier (most growth): horizontal API pods + CDN absorbs the long tail; trending/suggestion cache hit rate goes up not down at scale. Postgres: vertical first, read replicas for analytical queries (~100M rows is comfortable on r6g.2xlarge). ES: shard count fixed at index creation; we reindex to a larger sharded index via alias swap when growth demands. Redis: cluster mode when single-node memory or QPS is a wall. Workers: HPA on queue depth handles upload bursts.

**Q52. What's the bottleneck first?**
Almost certainly write QPS to Postgres on the favorites path, because every favorite/unfavorite takes a pessimistic lock on the media row — viral content creates contention. Mitigation order: (1) batch-fold favorite events into a per-second aggregator, (2) move `favorite_count` to Redis with periodic flush to Postgres, (3) shard favorites by `media_id` hash. Search QPS is much more cacheable.

**Q53. How would you shard Postgres?**
Application-level sharding by `user_id` for user-owned data (favorites, collections, refresh_tokens). Media by `id` hash — but media has FK from many tables, so we'd denormalise more. Realistically: we'd push vertical scaling (db.r7g.16xlarge) and read replicas as far as they go (further than people think — easily 100M-1B rows for our workload) before introducing sharding complexity.

**Q54. Hotspot mitigation strategies?**
Read hotspot on a viral media: CDN + Redis cache layer per media id (short TTL). Read hotspot on a popular channel: per-username cache. Write hotspot on the favorite counter: switch from synchronous Postgres increment to per-media in-Redis counter with periodic flush. Read hotspot on a popular search term: cache the response per (q, type, sort) tuple in Redis with 10s TTL.

**Q55. How would you go multi-region?**
First, identify what needs to be co-located vs replicated. Auth + user data: globally replicated (Aurora Global / Spanner) with sticky-region routing for writes. Media metadata: same. Search: per-region ES cluster, populated by per-region outbox. Renditions: S3 cross-region replication + per-region CDN. Uploads: route to nearest region. Hardest part: cross-region invalidation for non-cache state.

---

#### D.10 Observability and operations

**Q56. How do you monitor this in production?**
Three layers. (1) Infrastructure: CPU, memory, disk, network per pod (Prometheus + Grafana or provider-native). (2) Application: Spring Boot Actuator + Micrometer exposing JVM, HTTP server, datasource pool, cache hit rate metrics. (3) Business: search latency p50/p95/p99, transcode queue depth, transcode success rate, outbox lag, cache hit rate per cache, 4xx/5xx rate per endpoint. Alerts on SLO burn rate (multi-window multi-burn-rate, Google SRE style).

**Q57. What's the most important metric you'd alert on?**
Outbox lag (`MAX(now - created_at) WHERE published_at IS NULL`). It's a leading indicator: if it climbs, ES indexing is broken, which means search is going stale. Most failures of the underlying system surface here before users notice. Pair it with transcode queue depth and DLQ depth for the async paths.

**Q58. How do you debug a slow search query?**
ES `?explain=true` gives you per-shard timing and scoring breakdown. Spring's Micrometer integration tags HTTP metrics with the endpoint, so we can see p95 climb. Distributed tracing (OpenTelemetry, deferred) would tie a client request to its ES query span. For pattern-level slowness: ES's slow-query log surfaces queries above a threshold.

**Q59. How do you do safe deploys?**
Rolling deploy with health checks: new pod starts, fails its health probe until Spring context is fully up, ALB only routes traffic when ready. Flyway migrations are forward-compatible (additive, no destructive changes during the window the old code is also running). Expand/contract pattern for renames: add new column, dual-write, switch reads, remove old column.

**Q60. What's in your runbook for a Sev1?**
First: stop the bleeding (revert deploy, scale up, disable feature flag). Second: triage (which SLO is burning, which component is symptom vs cause). Third: investigate (logs, metrics, traces). Fourth: communicate (status page, internal updates). Fifth: post-incident review (blameless, action items tracked).

---

#### D.11 Specific tradeoffs and deep dives

**Q61. Walk me through the outbox pattern in detail.**
Same transaction writes the domain row + an `outbox_events` row capturing the change. After commit, both are durable. A scheduled poller (`@Scheduled(fixedDelay=2s)`) does `SELECT … WHERE published_at IS NULL ORDER BY created_at LIMIT 100`. For each row, it calls the indexer (Postgres → MediaDocument → ES upsert), then sets `published_at = now`. Failures leave `published_at = NULL` for retry on the next tick. ES upserts are idempotent so over-publishing is safe.

**Q62. Why not use Postgres's LISTEN/NOTIFY instead of polling?**
LISTEN/NOTIFY is async and not durable — if the listening pod is down when NOTIFY fires, the event is lost. The outbox row in the DB *is* the durable signal; polling is a recovery mechanism that handles missed wakeups gracefully. LISTEN/NOTIFY could be a latency optimisation (wake the poller immediately rather than waiting 2s) but isn't a correctness gain.

**Q63. How does your in-memory rate limiter work? Why not Bucket4j?**
Per-key `ConcurrentHashMap<UUID, TokenBucket>`. Each bucket holds N tokens (burst), refills at rate/sec. `tryAcquire` is synchronised per bucket (per-key contention, not global). On deny, returns `Retry-After` seconds. We chose in-memory because we're single-instance today; Bucket4j-Redis is a one-class swap when we go multi-instance. The simpler in-memory version avoided a dependency and the Redis round-trip per request (sub-ms but real at high QPS).

**Q64. Token bucket vs leaky bucket?**
Token bucket allows bursts (you can spend 60 tokens at once if the bucket is full). Leaky bucket smooths to a constant rate (regardless of accumulated capacity). For a developer API where occasional bursty bursts are reasonable (e.g., a search-as-you-type feature firing 5 requests in 200ms), token bucket fits better. For protecting downstream from overload, leaky bucket is safer.

**Q65. Why pessimistic locking for `favorite_count` and not optimistic?**
Optimistic would mean retrying on `OptimisticLockException`, which works fine for low contention but converts into retry storms under high contention (viral media). Pessimistic serialises the writes — under high contention you get back-pressure (slow writes) instead of compounding retries. We trade some write throughput on contended rows for predictable behaviour.

**Q66. What's the failure mode of the popularity recompute job?**
The job uses ShedLock (planned, not yet wired) so only one pod runs it. If it crashes mid-batch, the next pod's tick re-runs it from the same 15-min lookback window — idempotent because we recompute fresh and skip-if-unchanged. Worst case: a missed recompute interval delays popularity-driven reranking by 5-10 minutes. Search keeps working with stale popularity scores.

**Q67. How do you handle a leaked API key?**
Detection signal: anomalous traffic from a key (geographic, rate, pattern). Action: client revokes via UI (`DELETE /auth/developer/keys/{id}`); subsequent requests with that key 401. Forensics: `developer_keys.last_used_at` + `usage` Redis counters give a timeline. Prevention: prefix scanning (the `fg_` prefix is intentionally scanner-friendly so GitHub's secret scanning catches commits with leaked keys).

---

#### D.12 Future evolution

**Q68. How would you add follows / following?**
New tables: `follows (follower_id, followee_id, created_at, PK (follower, followee))`. New endpoints: POST/DELETE `/users/{username}/follow`, GET `/users/me/followers`, `/users/me/following`. Profile responses get `followerCount` + `followingCount` (counter on `users` updated transactionally, same pattern as favorites). New feed endpoint: `/users/me/feed` showing media from followed creators — fan-out-on-write or fan-out-on-read depending on follower count distribution.

**Q69. How would you add notifications?**
Add a `notifications` table per-user. Producer side: relevant slice operations (someone favorited your media, someone followed you) publish events via the outbox — but to a new event-type pipeline that fans out to multiple consumers (notification writer, push-notification sender, email). This is the moment we'd introduce RabbitMQ or Kafka for the outbox-event distribution layer.

**Q70. How would you add live-streaming GIF creation?**
Materially different architecture. WebRTC or HLS ingest, real-time transcoding (per-segment), edge-distribution. Probably a separate service — the current monolith's deployment cadence and ops profile don't fit a real-time pipeline. Would influence the modular monolith → microservices break-up decision.

**Q71. How would you A/B test ranking changes?**
Tag each request with an experiment bucket (deterministic hash of userId), have `SearchService` consult the bucket and pick a ranking variant. Log search → click events to an analytics pipeline (we don't have one yet — Kafka-fed warehouse). Compute CTR per bucket offline. Promote winners by changing the default. Requires a feature-flag system (LaunchDarkly / Unleash) and an analytics pipeline — both deferred.

**Q72. How would you support GIF generation from videos (creator uploads MP4, we make a GIF)?**
Already supported via the transcode pipeline — FFmpeg produces the GIF rendition from any video input. The PRD asks for this; v1 does it. Future enhancement: trim controls, "make GIF from this 5-second window" UX → adds a `trimStart` / `trimEnd` field to the metadata request, transcode worker honours it.

---

#### D.13 Closing reflections

**Q73. What's the riskiest part of this design?**
The outbox poller's correctness under all failure modes. If we miss the `SKIP LOCKED` semantics, two pollers can double-publish; if a publisher crashes between writing the row and committing the tx, no row is written; if the indexer ack is lost between successful ES write and the `published_at = now` update, we re-publish (safe because idempotent). All of these are handled today but they're the bugs we'd lose sleep over.

**Q74. What would you do differently if starting today?**
Probably add Testcontainers-backed integration tests from day one — we had three bugs in Slice 3 that would have been caught at build time. Also: stand up OpenTelemetry tracing earlier, before we need it to debug something.

**Q75. What part of this design are you least confident about?**
The popularity recompute formula (`log(1 + favorite_count*3 + view_count) * exp(-age_days/7)`) is a guess. We'll need to tune weights and decay based on actual user engagement data. The architecture supports rapid iteration (change formula → next batch reranks → outbox → ES) but the formula itself is a hypothesis until validated.

---

### E. Architecture Decision Records

Every meaningful decision in the system, written ADR-style. Each entry is short by design — context, decision, rationale, consequences — so this section reads as a reference, not a story.

**Conventions**
- *Status* — Accepted (in production code), Deferred (decided to defer; document the trigger that flips it), Superseded (replaced by a later ADR).
- *Trigger* (where present) — the concrete future event that would cause us to revisit the decision.

---

#### ADR-001: Modular monolith over microservices
**Status:** Accepted · **Slice:** Pre-1
**Context.** Greenfield codebase; team of ~1–3; six bounded contexts in the PRD (search, media, users, favorites, channels, developer).
**Decision.** Single Spring Boot deployable with package-by-feature boundaries (`com.flashgif.search`, `…media`, etc.). Modules talk only via service interfaces; no cross-module repository access. ArchUnit enforcement deferred.
**Rationale.** Microservices day-one would pay all the operational tax (six deploy targets, six runbooks, six dashboards, distributed tracing, schema-evolution coordination) for none of the scaling benefit at our size. Module boundaries inside one process give us the extraction option without the cost.
**Consequences.** Single CI build, single deploy, single observability surface. When (if) load justifies extraction we have natural seams (e.g. transcode worker → standalone) without refactoring domains.
**Trigger to revisit.** Any single module needs >10× the deployment cadence of the others, or its scaling profile diverges (GPU for transcode, isolated rate-limit infra for dev API).

#### ADR-002: PostgreSQL as the system of record
**Status:** Accepted · **Slice:** 1
**Context.** Need ACID for user accounts, media metadata, refresh tokens; JSONB fields (`rendition_urls`, `social_links`) are useful but not the dominant pattern; relational integrity (FK cascades on user/media delete) matters.
**Decision.** Postgres 16 + Flyway migrations + Spring Data JPA.
**Rationale.** Mongo or a document store would force manual integrity or 2PC-equivalents. Postgres handles our scale (100M rows) on a single primary comfortably; JSONB columns give us schemaless escape hatches without giving up ACID.
**Consequences.** Schema evolution requires forward-compatible migrations (we use the expand/contract pattern). Read replicas + connection pool tuning are the natural first scaling lever.

#### ADR-003: Elasticsearch as a read-only secondary, synced via outbox
**Status:** Accepted · **Slice:** 1
**Context.** Postgres FTS handles match + stem but not (a) typo-tolerant fuzzy search, (b) `search_as_you_type` autocomplete with edge n-grams, (c) ranking that blends relevance with a recomputed popularity score via `function_score`.
**Decision.** Separate Elasticsearch cluster holding a denormalised `media_v1` index, kept in sync from Postgres via the transactional outbox pattern.
**Rationale.** Two storage engines for two access patterns. Postgres for the authoritative write path; ES for the read-mostly search path. The outbox eliminates the dual-write problem.
**Consequences.** Eventual consistency on the search index (p95 lag ≤5s via the 2-second poller). Operational cost: a second stateful service to monitor, back up, and reindex.

#### ADR-004: Transactional outbox over CDC for ES sync
**Status:** Accepted · **Slice:** 1
**Context.** Need to bridge Postgres writes to Elasticsearch without dual-write inconsistency.
**Decision.** Application writes an `outbox_events` row in the same transaction as the domain change. A scheduled poller drains the outbox and writes to ES.
**Rationale.** Outbox is simpler to operate than Debezium + Kafka Connect, gives us application-level control over event shapes, and is portable across DBs. CDC becomes interesting at much larger scale or when many consumers want the same event stream.
**Consequences.** One scheduled task to monitor (lag metric). At-least-once delivery → consumers must be idempotent (ES upserts are, by id).
**Trigger to revisit.** A second consumer needs the same event stream (analytics, notifications), AND combined throughput exceeds the poller's batch capacity.

#### ADR-005: RabbitMQ over Kafka for the transcode queue
**Status:** Accepted · **Slice:** 2
**Context.** Single producer (the upload completion path), single consumer pool (FFmpeg workers); ≤10/s sustained throughput; no replay requirements.
**Decision.** RabbitMQ direct exchange + durable queue + DLQ. Persistent messages, prefetch=1, default-requeue-rejected=false.
**Rationale.** Kafka shines at high throughput, replay, and many-consumer fan-out. None apply here. RabbitMQ is operationally simpler, has good Spring AMQP integration, and is appropriate to scale.
**Consequences.** If we ever need event-sourced workflows (e.g. user activity → analytics + recommendations + notifications), we'll add Kafka alongside Rabbit, not replace it.

#### ADR-006: JWT access + opaque refresh tokens (mixed mode)
**Status:** Accepted · **Slice:** 3
**Context.** Need stateless access checks (so every request doesn't hit the DB) but easy revocation on account compromise.
**Decision.** Access token = HS256 JWT, 15-min TTL, validated locally. Refresh token = 256-bit opaque random, SHA-256 hashed in `refresh_tokens` table, 30-day rotating.
**Rationale.** JWT access gives us stateless validation. Opaque refresh gives us trivial revocation (delete the row) without a JWT blacklist defeating the stateless benefit. Mixed mode is intentional.
**Consequences.** Revocation lag = at most 15 minutes (access TTL). For instantaneous revocation we'd need a Redis-backed deny list keyed on `jti`, checked by the JWT filter; the structure supports adding this later without code change.

#### ADR-007: httpOnly cookies via Next.js Route Handler proxy (not localStorage)
**Status:** Accepted · **Slice:** Web 2
**Context.** Web client needs to attach the access token to backend requests; browser must NOT be able to exfiltrate tokens via XSS.
**Decision.** Tokens stored only in httpOnly cookies set by Next.js Route Handlers. Browser-side code calls same-origin Next.js routes; route handlers read the cookie and forward to Spring with `Authorization: Bearer`.
**Rationale.** httpOnly is the only XSS-proof storage. Route Handler proxying keeps the architecture clean (no special CORS, no token-in-URL flows). Costs one same-origin hop per call.
**Consequences.** Every authed endpoint needs a Route Handler (boilerplate cut via `proxyToBackend` helper). Public endpoints stay direct to Spring to avoid the hop. The cookie is path-scoped: refresh cookie only goes to `/api/auth/*`.

#### ADR-008: SHA-256 (not BCrypt) for refresh tokens and API keys
**Status:** Accepted · **Slice:** 3, 6
**Context.** Refresh tokens and API keys are 256-bit random, generated server-side.
**Decision.** Store `SHA-256(rawToken)` as bytea. Lookup is hash-equality.
**Rationale.** BCrypt's value is slowing down brute-force of low-entropy human-chosen passwords. High-entropy random tokens are brute-force-infeasible regardless of hash cost. BCrypt on a 256-bit secret adds ~100ms per auth check for zero security benefit.
**Consequences.** Lookups are constant-time. Same algorithm used in two places (`refresh_tokens.token_hash`, `developer_keys.key_hash`) — consistent mental model.

#### ADR-009: Two SecurityFilterChain beans, scoped by path
**Status:** Accepted · **Slice:** 3, 6
**Context.** End users authenticate via JWT, third-party developers via API key, and the two should have distinct rate limits + filters.
**Decision.** `@Order(1) developerChain` with `securityMatcher("/api/v1/developer/**")`; `@Order(2) userChain` as the catch-all. Spring routes requests to the first matching chain.
**Rationale.** One chain trying to handle both credential types would be confusing and would couple rate-limit decisions to credential-decoding decisions. Per-chain configuration is cleaner.
**Consequences.** Key management endpoints (`/api/v1/auth/developer/keys`) deliberately stay in the user chain — issuing a key is a user operation, not a key operation.

#### ADR-010: Pessimistic locking for counter mutations
**Status:** Accepted · **Slice:** 4
**Context.** `media.favorite_count` is mutated on every favorite/unfavorite; high contention on viral content.
**Decision.** `SELECT … FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` before incrementing.
**Rationale.** Optimistic locking + retry-on-conflict compounds into retry storms under hot contention. Pessimistic serialises the writes — backpressure becomes the observable effect, not lost updates.
**Consequences.** Slight throughput cap on hot rows. Mitigation paths if it bites: (a) move counter to Redis with periodic flush, (b) batch-fold per-second favorite deltas.

#### ADR-011: In-memory token bucket rate limiter (not Bucket4j-Redis yet)
**Status:** Deferred · **Slice:** 6
**Context.** Developer API needs per-key rate limiting (60 req/min default). Multi-instance deployment would require distributed counter state.
**Decision.** Hand-rolled token bucket in `ConcurrentHashMap<UUID, Bucket>` for now. Bucket4j-Redis is the documented upgrade path.
**Rationale.** Single-instance correct today; avoids dep-resolution risk (we hit issues with Bucket4j coords in Slice 0); no Redis round-trip per request.
**Consequences.** Rate-limit state is lost on pod restart (effectively resets all buckets to full). Acceptable in dev; would mean burst capacity on first request after deploy in prod.
**Trigger to revisit.** Second API pod is provisioned, OR rate-limit accuracy becomes a customer-facing SLA.

#### ADR-012: Per-prefix S3 bucket policy (renditions public, uploads private)
**Status:** Accepted · **Slice:** 2 (latent bug; fixed in session 11)
**Context.** Renditions are CDN-style content (anyone with the URL should be able to GET them). Originals are private uploader assets.
**Decision.** Bucket policy on `flashgif-media` allows `s3:GetObject` from `*` on `renditions/*` only. `uploads/*` remains private; backend signs read URLs as needed.
**Rationale.** Public renditions cache trivially in any CDN. Private originals keep the source asset under uploader control.
**Consequences.** Bucket policy is applied idempotently in `BucketBootstrapper` on every startup. In production the same prefix policy is set on the real S3 bucket via IaC; CDN fronts `renditions/*`.

#### ADR-013: Direct browser → S3 upload via presigned PUT (not proxy through backend)
**Status:** Accepted · **Slice:** 2
**Context.** Up to 100 MB file uploads; backend must not become a bandwidth bottleneck.
**Decision.** Backend issues short-lived (15-min) presigned PUT URL; browser uploads directly to S3.
**Rationale.** A 50 MB upload via backend ties up an API pod's connection and bandwidth for the upload duration. Presigned URLs offload the bytes entirely.
**Consequences.** Browser → S3 traffic doesn't traverse our backend. Two extra round trips at the start (reserve, complete) but they're tiny.

#### ADR-014: Explicit `/complete` callback (not S3 event notifications)
**Status:** Accepted · **Slice:** 2
**Context.** Need to know when the browser's PUT to S3 finished so we can enqueue transcode.
**Decision.** Client POSTs `/api/v1/media/upload/{id}/complete` after a successful PUT. Backend HEADs S3 to verify object presence, then enqueues.
**Rationale.** S3 events are async and not deduplicated; client-driven completion is explicit, testable, and retryable. S3 events are appropriate when there's no client (e.g. third-party drop-in uploads).
**Consequences.** If the client crashes between PUT and `/complete`, the upload row stays in `AWAITING_UPLOAD` indefinitely. A sweeper job is the deferred mitigation.

#### ADR-015: Two-table upload state machine (`media_uploads` + `media`)
**Status:** Accepted · **Slice:** 2
**Context.** Uploads go through several states before becoming searchable media. Failed/abandoned uploads should never pollute the searchable corpus.
**Decision.** `media_uploads` table holds the pipeline state machine (`AWAITING_UPLOAD → UPLOADED → PROCESSING → READY → PUBLISHED`, with `FAILED` terminal). `media` table is created only on successful metadata submission; it's what's indexed in ES.
**Rationale.** Separates concerns — pipeline lifecycle vs published-entity. Search code never has to filter out half-baked uploads.
**Consequences.** Two tables, two services (`UploadService`, `PublishService`). One extra row per published media. Worth it.

#### ADR-016: snake_case JSON across the entire public API
**Status:** Accepted (post-correction) · **Slice:** Web 1 + follow-up
**Context.** `@Schema(name = "...")` annotations documented snake_case names in the OpenAPI spec, but Jackson was emitting camelCase by default, creating a contract mismatch.
**Decision.** `spring.jackson.property-naming-strategy: SNAKE_CASE` globally. TS types match. The OpenAPI spec is now accurate.
**Rationale.** snake_case is the dominant REST convention (Stripe, GitHub, etc.). Aligning the wire format with the documented spec keeps third-party developers' tooling honest.
**Consequences.** Field names like `User.isVerified` (Lombok strips `is`) become `verified`, not `is_verified` — worth knowing. `Map<String, String>` keys are NOT transformed (Jackson naming strategy only affects bean properties), which keeps outbox payload keys stable.

#### ADR-017: shadcn/ui (copy-paste components) over an off-the-shelf UI library
**Status:** Accepted · **Slice:** Web 1
**Context.** Need a component library that's themeable and won't lock us into a specific design system.
**Decision.** Drop shadcn/ui-style primitives (Button, Input) directly into `components/ui/`. No runtime UI library dependency.
**Rationale.** We own the source; theming is via Tailwind CSS variables; no dep upgrades to chase; no bundle-size cost we can't see in our own code.
**Consequences.** We're responsible for accessibility + ARIA on our primitives. shadcn's templates handle this; we use them verbatim.

#### ADR-018: `openapi-typescript` (types only) over a full client generator
**Status:** Accepted · **Slice:** Web 1
**Context.** Need TypeScript types for backend response shapes so the client compiles cleanly against the API contract.
**Decision.** `openapi-typescript` to generate `lib/api/types.ts`. We hand-write thin `fetch` wrappers (`apiFetch`, `authedFetch`) on top.
**Rationale.** Full client generators (Orval, openapi-fetch) generate hooks, validators, runtime stubs — more dep surface, harder to customize, especially around our cookie-proxy auth model.
**Consequences.** Two-stage type-safety: openapi types describe the wire; our hand-written endpoint functions describe what we actually call. Drift between the two is caught when the generated types fail to assign.

#### ADR-019: React Query for server state, Zustand for ephemeral UI state
**Status:** Accepted · **Slice:** Web 1
**Context.** Two distinct categories of state — server-derived (caches, refetches, mutations) and ephemeral UI (modal open, theme).
**Decision.** TanStack Query owns everything that comes from the backend. Zustand handles tiny UI state needs (~1 KB lib).
**Rationale.** Mixing server state into Redux/Zustand has been the standard footgun for years; React Query was built specifically to fix that. Zustand for the truly local state stays out of React Query's way.
**Consequences.** Devs need to know which lives where. The rule of thumb: "if it can be re-fetched, it's React Query".

#### ADR-020: Next.js App Router with server components for SEO-critical pages
**Status:** Accepted · **Slice:** Web 1, 5
**Context.** Trending, search, and channel pages benefit from SEO indexing. Auth and dashboard pages don't.
**Decision.** App Router. Pages default to server components; client components opt in with `"use client"`. SEO pages SSR; interactive pages are client islands.
**Rationale.** Server components stream HTML that already has data — no spinner-then-content. SEO crawlers index real content. App Router's Route Handlers also give us the auth proxy.
**Consequences.** "Mixed module" patterns (e.g., `lib/api/channels.ts` has both server-callable and client-only functions) need careful directive placement.

#### ADR-021: Per-rendition non-fatal transcode (not all-or-nothing)
**Status:** Accepted (post-bug) · **Slice:** 2 + session 11 fix
**Context.** Original Slice 2 design failed the whole transcode job if any single encoder errored. Realistic FFmpeg installs have varying encoder coverage (Homebrew lacks `libwebp` by default).
**Decision.** Each rendition runs in a `tryRendition(…)` wrapper. Per-encoder failure logs a warning and skips. The job only fails (and routes to DLQ) when *all* renditions fail.
**Rationale.** Transcoder pipelines should default to "best-effort per output". `MediaCard` already falls back webp → gif → poster, so a missing rendition is invisible to users.
**Consequences.** Adding new renditions (HD, vertical, etc.) inherits the resilience for free. Operations: alarm on "all-renditions-failed" rate, not individual rendition failures.

#### ADR-022: Avoid Postgres-vendor column types in app-facing schemas
**Status:** Accepted (post-bug) · **Slice:** 3 + session 4 fix
**Context.** Initial schemas used `citext` (email) and `inet` (refresh_tokens.ip) because they were the "right" Postgres types. Hibernate's strict schema validator rejected the JDBC type mismatch (String → VARCHAR vs CITEXT/INET → OTHER).
**Decision.** Default to portable column types (`varchar`) and handle case-insensitivity or IP-formatting at the app layer (e.g., `UserService.normalizeEmail()`).
**Rationale.** Vendor-specific types create friction with ORMs that assume JDBC-standard mappings. The portable choice keeps tests, ORMs, and replication tooling happier; the small app-layer cost is worth it.
**Consequences.** `users.email` is `varchar(254)` with lowercase normalization; `refresh_tokens.ip` is `varchar(45)` (IPv6 max). No CITEXT/INET-specific operators available — fine for our patterns.

#### ADR-023: Publish-after-commit pattern for async event dispatch
**Status:** Accepted (post-bug) · **Slice:** 2 + session 11 fix
**Context.** `UploadService.markUploaded` was `@Transactional` and called `dispatcher.dispatch(...)` directly inside the transaction. The RabbitMQ consumer (same JVM) raced ahead and read the upload row before the writing tx had committed, seeing stale state.
**Decision.** Wrap the dispatch in `TransactionSynchronizationManager.registerSynchronization(...) { afterCommit() }`. The Rabbit publish only happens after the JDBC commit.
**Rationale.** "Publish after commit" is the textbook pattern for fire-and-forget side effects on writes. The consumer sees the committed state and the state-machine transition succeeds.
**Consequences.** Any future service method that publishes to RabbitMQ/Kafka/whatever from inside a `@Transactional` boundary must use the same pattern. Generalising via Spring's `@TransactionalEventListener(AFTER_COMMIT)` is the cleaner upgrade if we add 3+ sites.

#### ADR-024: Redis cache with default-typed `ObjectMapper` (separate from web mapper)
**Status:** Accepted (post-bug) · **Slice:** 1 + session 8 fix
**Context.** `GenericJackson2JsonRedisSerializer` initialised with the default web `ObjectMapper` lost generic type info: `List<MediaSummary>` round-tripped as `List<LinkedHashMap>`, blowing up the subsequent HTTP serialization with `IllegalArgumentException: object is not an instance of declaring class`.
**Decision.** A dedicated cache `ObjectMapper` with `activateDefaultTyping(...)` + a `BasicPolymorphicTypeValidator` allowing `com.flashgif.`, `java.util.`, `java.time.`. Cache values now carry `@class` metadata; HTTP responses stay clean (separate mapper, no `@class` pollution).
**Rationale.** Type info has to survive a serialize/deserialize round-trip; without it, generic types erase to LinkedHashMap. Two mappers keep cache concerns out of the wire format.
**Consequences.** Cache invalidation on type renames/moves: `FLUSHDB` is the safe step. Documented in the runbook.

#### ADR-025: Modal/page split for the upload UX (modal trigger from header "+")
**Status:** Accepted · **Slice:** Web 3
**Context.** Pinterest/Giphy/Tenor-style upload UX is contextual — you stay on the page you were browsing.
**Decision.** Header `+ Upload` button (auth-only) opens a 3-stage modal (dropzone → upload+poll → metadata form). On publish success, redirect to `/channels/[me.username]`.
**Rationale.** Modal preserves user context. Single modal handles the entire pipeline so the user sees one cohesive flow.
**Consequences.** Modal must be viewport-aware (we cap at `max-h-[90vh]` with internal scroll). Mobile users get the same modal — works on small screens because of the cap.

#### ADR-026: Server-side SSR pre-fill for `useMe` (no Login → UserMenu flash)
**Status:** Accepted · **Slice:** Web 2
**Context.** First paint shouldn't flash "Login / Sign up" buttons to a logged-in user.
**Decision.** Root `layout.tsx` is a server component that reads the cookie, calls Spring `/users/me` server-side, and seeds React Query with the result. Header receives the user as a prop and is correct on first render.
**Rationale.** The cost is one server-to-server call per page render. The UX win is significant — no perceived "loading" period for an authenticated user.
**Consequences.** Server-side `getCurrentUser()` bypasses the Route Handler proxy (direct to Spring). One more place that needs to know about Spring's URL.

#### ADR-027: Optimistic mutations for favorites (snapshot + rollback)
**Status:** Accepted · **Slice:** Web 4
**Context.** Heart-button click should feel instant; backend round-trip is ~100ms.
**Decision.** React Query `useMutation` with `onMutate` snapshot, optimistic cache update, `onError` rollback, `onSettled` invalidation. The card flips state instantly.
**Rationale.** Favoriting is idempotent — the worst case on failure is one click "didn't take" and the user retries. Worth the perceived-speed win.
**Consequences.** Any operation we make optimistic must be idempotent and have a cheap rollback. Document the pattern; don't apply to writes with side effects (e.g., publishing media).

#### ADR-028: Defer `GET /api/v1/media/{id}` (favorites + collection items show placeholders)
**Status:** Deferred · **Slice:** Web 4
**Context.** Favorites and collection-items endpoints return `media_id`s; web has no way to rehydrate full media rows from just IDs without a per-id backend endpoint.
**Decision.** Render `MediaTilePlaceholder` (ID + timestamp) for now. Add `GET /api/v1/media/{id}` as a follow-up, then swap the placeholder for `<MediaCard>`.
**Rationale.** Shipping Slice 4 with full visuals would block on a backend change; shipping with placeholders unblocks the rest of the slice immediately.
**Consequences.** Favorites page is functional but ugly. Single backend endpoint + one React Query hook closes the gap.
**Trigger to revisit.** Any user feedback about the favorites page looking unfinished, OR before mobile clients start consuming favorites.

#### ADR-029: Hand-roll Next.js scaffold (skip `create-next-app`)
**Status:** Accepted · **Slice:** Web 1
**Context.** `create-next-app` is interactive, opinionated about ESLint/Prettier configs, and tends to include boilerplate we'd have to remove.
**Decision.** Hand-wrote `package.json` + `tsconfig.json` + `next.config.mjs` + `tailwind.config.ts` + `postcss.config.mjs` + `globals.css` directly. Pinned exact versions.
**Rationale.** Cleaner starting state, exact dep versions, no template artifacts. ~10 minutes saved vs cleanup.
**Consequences.** When we upgrade Next majors we update the configs manually rather than re-running the generator. Fine — we're not far enough from the template that the diff is meaningful.

#### ADR-030: Defer Testcontainers integration tests (acknowledged debt)
**Status:** Deferred · **Slice:** 0
**Context.** Unit tests cover the easy stuff; the bugs that actually bite are at the integration boundaries (DB, Rabbit, S3, ES). Testcontainers would catch most of them at `./gradlew build` time.
**Decision.** Defer until a slice without a feature deadline. Document the debt visibly in every bug post-mortem.
**Rationale.** Initial velocity over correctness investment. Six backend slices in 7 days proves the velocity hypothesis was right.
**Consequences.** **Nine production-relevant bugs caught this session alone** (Bugs 1–9 in progress.md), every one would have been caught by integration tests. The deferred-tests debt is the largest unpaid liability in the system. Prioritize before any new feature work.
**Trigger to revisit.** As soon as no feature is mid-flight.

---

#### Summary index

| ADR | Title | Status |
|---|---|---|
| 001 | Modular monolith over microservices | Accepted |
| 002 | PostgreSQL as system of record | Accepted |
| 003 | Elasticsearch as read-only secondary | Accepted |
| 004 | Transactional outbox over CDC | Accepted |
| 005 | RabbitMQ over Kafka | Accepted |
| 006 | JWT access + opaque refresh (mixed mode) | Accepted |
| 007 | httpOnly cookies via Next.js Route Handler proxy | Accepted |
| 008 | SHA-256 (not BCrypt) for high-entropy tokens | Accepted |
| 009 | Two SecurityFilterChain beans, scoped by path | Accepted |
| 010 | Pessimistic locking for counter mutations | Accepted |
| 011 | In-memory token bucket (not Bucket4j-Redis yet) | Deferred |
| 012 | Per-prefix S3 bucket policy | Accepted |
| 013 | Direct browser → S3 presigned PUT upload | Accepted |
| 014 | Explicit `/complete` callback over S3 events | Accepted |
| 015 | Two-table upload state machine | Accepted |
| 016 | snake_case JSON across public API | Accepted |
| 017 | shadcn/ui (copy-paste) over UI library | Accepted |
| 018 | `openapi-typescript` types only (no client gen) | Accepted |
| 019 | React Query for server state + Zustand for UI | Accepted |
| 020 | Next.js App Router + server components | Accepted |
| 021 | Per-rendition non-fatal transcode | Accepted |
| 022 | Avoid Postgres-vendor column types | Accepted |
| 023 | Publish-after-commit for async dispatch | Accepted |
| 024 | Default-typed cache `ObjectMapper` (separate) | Accepted |
| 025 | Modal upload UX, post-publish redirect to channel | Accepted |
| 026 | SSR pre-fill for `useMe` (no flash) | Accepted |
| 027 | Optimistic favorite mutations (snapshot + rollback) | Accepted |
| 028 | Defer `GET /media/{id}` (placeholder tiles) | Deferred |
| 029 | Hand-roll Next.js scaffold | Accepted |
| 030 | Defer Testcontainers integration tests | Deferred (debt) |