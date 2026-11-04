# Deployment and operations

ConnectOne is a training system; there is no production environment. This documents how it *would* be built, configured, run and
rolled back, and what is verified locally.

## Build
```powershell
.\mvnw.cmd -DskipTests package         # target\connectone-0.0.1-SNAPSHOT.jar (executable Spring Boot jar)
cd frontend; npm ci; npm run build     # frontend\dist (static files; serve behind any web server and proxy /api to the API)
```

## Configuration (environment variables, never in git)
| Variable | Purpose | Default |
|---|---|---|
| `CONNECTONE_DB_PASSWORD` | DB password for `connectone_app` (required) | – |
| `CONNECTONE_JWT_SECRET` | HS256 secret, ≥32 chars. Unset ⇒ random per start (tokens die on restart) | random |
| `CONNECTONE_PORT` | API port | 18080 |
| `CONNECTONE_CACHE_TYPE` | `none` or `redis` | none |
| `CONNECTONE_REDIS_HOST/PORT` | Redis location | localhost / 6380 |
| `CONNECTONE_SEED_ENABLED/PASSWORD` | small fixture seeding (local DBs only) | false |
DB URL, pool (max 10, 5 s acquire timeout, 20 s leak detection) are in `application.yml`; override with Spring's env binding (`SPRING_DATASOURCE_URL`…).
Profile `json` switches logs to ECS JSON.

## Run
`java -jar target\connectone-0.0.1-SNAPSHOT.jar` with the variables above. On start Flyway migrates, Hibernate validates, jobs begin.
Graceful shutdown is enabled. Run **one** instance (see KNOWN_LIMITATIONS: two jobs are not multi-instance safe).

## Health and monitoring
- `/actuator/health` (public: UP/DOWN), `/actuator/health/liveness`, `/actuator/health/readiness`. Admin token adds component detail (`db`, `cache` incl. Redis reachability, `diskSpace`).
- Redis being down does **not** change health (fail-open); the `cache` component says so.
- Metrics (admin): `/actuator/metrics` — `http.server.requests`, `hikaricp.connections.*`, `jvm.*`, and business counters `connectone.payment.confirmed{outcome}`, `connectone.refund.created`, `connectone.payment.expired`, `connectone.payment.reconciled`, `connectone.coupon.*`.
- Logs: access log `connectone.access` (method, path, status, durationMs), audit `connectone.audit`, errors with `correlationId`; PostgreSQL logs statements > 250 ms and lock waits.
- Suggested alerts: readiness ≠ UP; 5xx rate; `hikaricp.connections.pending > 0` for 1 min; `connectone.payment.confirmed{outcome=FAILED}` spike; `payments needing reconciliation` (dashboard) > 0 for a day; stuck tickets.

## Database operations
- Migrations are forward-only (`V7__…`). Rollback = deploy the previous jar **and** a compensating forward migration; never edit applied files. Take `pg_dump` before risky migrations:
  `docker exec connectone-postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > connectone_dev.dump`.
- Slow-query diagnostics are enabled on the local instance (`log_min_duration_statement=250`, `pg_stat_statements`).
- The dataset script is for non-production data only and refuses any database but `connectone_dev` in `connectone-postgres`.

## Release and rollback checklist
1. `scripts\test.ps1` green; `npm test`, `npm run build` green. 2. `pg_dump`. 3. Deploy jar (Flyway runs). 4. `readiness` UP, smoke `GET /api/v1/recharge-plans`, admin dashboard loads.
5. Rollback: stop, redeploy previous jar; if a migration shipped, apply its compensating migration or restore the dump.

## Measured baseline (10,000-customer dataset, laptop, 10 virtual users, 12 s, `scripts\loadtest.mjs`)
All runs: **0 errors**. Run A = cache off (right after a cold start, so partly JIT warm-up); Run B = Redis cache on, warm JVM. They are *not* isolated A/B experiments.

| Endpoint | A p50 / p95 (ms) | B p50 / p95 (ms) |
|---|---|---|
| GET /recharge-plans (public) | 37 / 127 | **10 / 24** |
| GET /customers/me | 67 / 335 | 26 / 63 |
| GET /recharges/me?size=10 (customer 1…50) | **208 / 656** | 124 / 218 |
| GET /payments/me (unpaged) | 82 / 376 | 34 / 72 |
| GET /notifications/me | 97 / 293 | 37 / 86 |
| GET /bookings/catalogue | 80 / 315 | 31 / 75 |
| ADMIN /admin/dashboard | 872 / 1358 | 417 / 1706 |
| ADMIN /customers?q=aarav | 598 / 3139 | 454 / 753 |
| POST /auth/login (BCrypt) | 776 / 1174 | 849 / 1096 |
| Throughput | 60 req/s | 160 req/s |
Redis outage: cached endpoint ≈ 330 ms instead of ≈ 13 ms (connect timeout; see KNOWN_LIMITATIONS). Login stays ≈ 0.8 s p50 in both runs (CPU-bound by design).
Use these as the "before" numbers for the Level 5 tickets; measure your own runs the same way.
