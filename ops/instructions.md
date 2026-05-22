# FlashGif — Local Infrastructure (Docker Compose)

This stack runs every backing service the FlashGif backend depends on. Bring it up before running `./gradlew bootRun` from `backend/`.

## Prerequisites
- **Docker** 24+ and the **Compose v2** plugin (`docker compose ...`, not `docker-compose`)
- **Free local ports:** `5432`, `9200`, `6379`, `5672`, `15672`, `9000`, `9001`
- ~4 GB free RAM (Elasticsearch alone reserves 512 MB heap)
- ~5 GB free disk for volumes under `ops/data/`

Verify with:
```bash
docker --version
docker compose version
```

## Services

| Service | Image | Host port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16.4-alpine` | `5432` | Primary OLTP store (source of truth) |
| `elasticsearch` | `elasticsearch:8.15.3` | `9200` | Media search index |
| `redis` | `redis:7.4-alpine` | `6379` | Cache + rate-limit backing store |
| `rabbitmq` | `rabbitmq:3.13-management-alpine` | `5672`, `15672` | Async media processing queue + mgmt UI |
| `minio` | `minio:RELEASE.2025-04-22T22-12-26Z` | `9000`, `9001` | S3-compatible object storage (dev) |

## Credentials (dev only — never reuse in real environments)

| Service | Username | Password | DB / Bucket |
|---|---|---|---|
| Postgres | `flashgif` | `flashgif` | DB: `flashgif` |
| RabbitMQ | `flashgif` | `flashgif` | vhost: `/` |
| MinIO | `flashgif` | `flashgif-secret` | Bucket: `flashgif-media` (created by the app on startup) |
| Elasticsearch | _(security disabled)_ | — | — |
| Redis | _(no auth)_ | — | — |

## Common commands

All commands assume your working directory is `ops/`.

### Start the stack
```bash
docker compose up -d
```
First run pulls images (~1.5 GB). Subsequent runs are seconds.

### Check health
```bash
docker compose ps
```
Every service should show `(healthy)` once warm-up completes (~30 s for Elasticsearch).

### Tail logs
```bash
docker compose logs -f                 # everything
docker compose logs -f elasticsearch   # one service
```

### Stop (preserves data)
```bash
docker compose stop
```

### Stop and remove containers (preserves data on disk)
```bash
docker compose down
```

### Nuke everything including data
```bash
docker compose down -v
rm -rf data/
```
Use this when you want a truly empty stack — e.g., to test a fresh migration path.

### Restart one service
```bash
docker compose restart rabbitmq
```

## Verifying each service

Run these from your host after `docker compose up -d`.

### Postgres
```bash
docker exec -it flashgif-postgres psql -U flashgif -d flashgif -c '\dt'
```

### Elasticsearch
```bash
curl -s http://localhost:9200/_cluster/health | jq
```
Should return `status: green` (or `yellow` on a single-node cluster — both are fine for dev).

### Redis
```bash
docker exec -it flashgif-redis redis-cli ping
# → PONG
```

### RabbitMQ
- Management UI: <http://localhost:15672> (login: `flashgif` / `flashgif`)
- CLI ping:
  ```bash
  docker exec -it flashgif-rabbitmq rabbitmq-diagnostics ping
  ```

### MinIO
- Console UI: <http://localhost:9001> (login: `flashgif` / `flashgif-secret`)
- Health:
  ```bash
  curl -s http://localhost:9000/minio/health/live -o /dev/null -w '%{http_code}\n'
  # → 200
  ```

## Data persistence

Volumes are bind-mounted to `ops/data/` (gitignored), so data survives container restarts and `docker compose down`. Backup is a directory copy:
```bash
tar czf flashgif-data-backup-$(date +%F).tgz data/
```

Restore by extracting in place while the stack is stopped.

## Connecting from the backend

The backend's `application.yml` already points at these endpoints with env-var overrides. To target a non-default host (e.g., remote dev box), export before `bootRun`:

```bash
export DB_URL=jdbc:postgresql://10.0.0.5:5432/flashgif
export ES_URIS=http://10.0.0.5:9200
export REDIS_HOST=10.0.0.5
export RABBIT_HOST=10.0.0.5
export S3_ENDPOINT=http://10.0.0.5:9000
./gradlew bootRun
```

## Troubleshooting

### Elasticsearch container exits with code 137
Out of memory. Either raise Docker's memory limit (Docker Desktop → Settings → Resources → Memory ≥ 4 GB) or drop the heap in `docker-compose.yml`:
```yaml
ES_JAVA_OPTS: "-Xms256m -Xmx256m"
```

### Port already in use
Something else on the host holds the port. Identify it:
```bash
lsof -nP -iTCP:5432 -sTCP:LISTEN
```
Stop the offender or change the host-side port mapping (left side of `"5432:5432"`).

### `docker compose ps` shows `(unhealthy)`
Tail that service's logs (`docker compose logs <name>`). Most commonly the volume in `data/<name>/` has stale or wrong-version data — wipe with `docker compose down -v && rm -rf data/<name>` and bring back up.

### MinIO bucket missing
The bucket `flashgif-media` is created by the backend on startup, not by compose. If the backend isn't running yet, create it manually via the console at <http://localhost:9001> or with the `mc` CLI:
```bash
docker run --rm --network host minio/mc \
  alias set local http://localhost:9000 flashgif flashgif-secret
docker run --rm --network host minio/mc mb local/flashgif-media
```

### RabbitMQ management UI 502
The container is still starting. Wait ~10 s and retry; `docker compose ps` should show `(healthy)` first.

## Production notes (for the ops team)

This compose file is **not** suitable for production:
- Hardcoded credentials in the file
- `xpack.security.enabled: false` on Elasticsearch
- No TLS anywhere
- No replication or HA — single-node Postgres/ES/Rabbit
- Volumes are local bind mounts, not managed storage
- MinIO single-node `server /data` — for prod, use real S3 or a 4+ node MinIO cluster in distributed mode

For production, treat this file as a reference for *what services we need* and provision the real equivalents (RDS Postgres, Elastic Cloud / managed OpenSearch, ElastiCache, Amazon MQ / CloudAMQP, S3) through your normal IaC.
