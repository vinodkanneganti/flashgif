# FlashGif (Giphy Clone)

A Giphy.com-style platform for searching, uploading, and sharing GIFs/short videos.

## Source of truth
- Product requirements: [prd.txt](prd.txt)
- System architecture (read this for the full picture): [architecture.md](architecture.md)
- Decision + build log: [progress.md](progress.md)

## Feature scope
1. **Search & Discovery** — trending grid, autocomplete suggestions, infinite-scroll results
2. **Media Upload & Transformation** — drag-and-drop upload, async FFmpeg transcoding, tagging
3. **User Collections & Favorites** — favorite GIFs, custom folders
4. **Developer API & Toolkit** — API keys, usage analytics, Swagger docs
5. **Creator Profile & Channel** — public channels with banner/avatar/uploads

## Tech stack
| Layer | Choice |
|---|---|
| Backend | Java 21 + Spring Boot 3.x (Gradle) |
| Auth | Spring Security + JWT (email + password only for v1) |
| Primary DB | PostgreSQL + Spring Data JPA + Flyway |
| Search | Elasticsearch + Spring Data Elasticsearch |
| Task queue | RabbitMQ (async media processing) |
| Cache / rate-limit | Redis + Bucket4j |
| Object storage | S3 (MinIO for local dev) |
| Upload path | Browser → S3 direct via presigned PUT URLs |
| Media processing | FFmpeg (worker process, same JAR, `worker` profile) |
| API docs | SpringDoc OpenAPI — Swagger UI at `/swagger-ui.html`; static `openapi.yaml` generated and committed to `docs/` for client codegen |
| Web frontend | Next.js 14 (App Router) |
| Mobile | iOS (Swift + SwiftUI), Android (Kotlin + Jetpack Compose) |

## Architecture

**Shape:** modular monolith. Single Spring Boot deployable, package-by-feature with strict boundaries (no cross-module repository access — services talk via interfaces). Extract to separate services later if load justifies.

**Backend modules:**
- `search` — Elasticsearch-backed search, trending, autocomplete
- `media` — upload orchestration, metadata, transcoding pipeline
- `users` — accounts, auth, JWT issuance
- `favorites` — favorites and collections
- `channels` — public creator profiles
- `developer` — API keys, usage analytics
- `infra` — shared config, security filter chains, persistence, messaging

**Data flow:**
- Postgres is source of truth for all entities
- Elasticsearch holds a denormalized `media` index, populated via **transactional outbox** (write `outbox_events` in same tx as the domain change; a poller publishes to ES)
- Redis caches trending lists, suggestion prefixes, and backs Bucket4j rate limiting
- RabbitMQ carries media processing jobs: `media.upload.completed` → FFmpeg worker → `media.transcode.completed`
- S3 holds binaries; browser uploads directly via presigned PUT; CDN fronts S3 for delivery

**Auth:**
- Two `SecurityFilterChain` beans: `/api/v1/**` (user JWT, BCrypt passwords, refresh-token rotation) and `/api/v1/developer/**` (hashed API keys)
- Different rate-limit tiers per chain

## Repository layout
```
giphyc/
├── backend/          # Spring Boot, Gradle
│   └── src/main/java/com/flashgif/
│       ├── search/   ├── media/    ├── users/
│       ├── favorites/├── channels/ ├── developer/
│       └── infra/
├── web/              # Next.js (later)
├── ios/              # (later)
├── android/          # (later)
├── ops/
│   ├── docker-compose.yml   # postgres, elasticsearch, redis, rabbitmq, minio
│   └── ffmpeg/
├── docs/
│   └── openapi.yaml         # generated from SpringDoc, committed for client codegen
├── prd.txt
└── CLAUDE.md
```

## Local development
- `cd ops && docker-compose up -d` — Postgres, Elasticsearch, Redis, RabbitMQ, MinIO
- `cd backend && ./gradlew bootRun` — API + worker (default profile runs both)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs` → snapshot to `docs/openapi.yaml` via Gradle task

## Build order (agreed)
Backend-first, vertical slices per feature:
1. **Search & Discovery** — search, trending, suggestions (this is the next slice)
2. Media upload + FFmpeg transcoding
3. Users + auth
4. Favorites + collections
5. Channels
6. Developer API + analytics

Then web, then mobile.

## Conventions
_To be added as the codebase grows._
