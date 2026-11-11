# Local environments and the deploy workflow

Three ways to run the platform locally, each teaching something different. Pick by what you are practising.

| Environment | What runs where | Start | Use it to practise |
|---|---|---|---|
| **DEV** (daily coding) | 3 JVMs on your host (`java -jar`), infra in containers (Postgres, Redis, Kafka), frontend via Vite | `scripts\start-all.ps1` + `cd frontend; npm run dev` | debugging in the IDE, fast iterations, logs in `logs\*.log` |
| **STAGING-LIKE** (deploy rehearsal) | everything in containers via Compose: images built from the Dockerfile, healthchecks, nginx serving the SPA | `docker compose up -d --build` | image builds, config via env vars, startup order, rolling a single service, rollback |
| **OBSERVED** (operations) | STAGING-LIKE + Prometheus + Grafana | `docker compose --profile observability up -d` | metrics, dashboards, "what does it look like when I break it" |

| URL | DEV | STAGING-LIKE |
|---|---|---|
| App (browser) | http://localhost:5173 | http://localhost:8081 |
| API gateway | http://localhost:18080 | http://localhost:18090 |
| core-service direct | http://localhost:18081 | http://localhost:18091 |
| notification-service direct | http://localhost:18082 | http://localhost:18092 |
| PostgreSQL | localhost:5434 | localhost:5435 |
| Prometheus / Grafana | – | http://localhost:9090 / http://localhost:3000 (admin/admin) |

Resources: the 2 GB Docker VM from `.wslconfig` is enough for DEV infra and for STAGING-LIKE *without* observability. For observability or to run STAGING-LIKE and DEV together, raise
`memory=` in `%USERPROFILE%\.wslconfig` to 5GB and run `wsl --shutdown` (this restarts every container, including other projects'; do it when you are ready).
Never run DEV and STAGING-LIKE at the same time on a small machine: stop one first (`scripts\stop-all.ps1`, `docker compose down`).

## DEV workflow (a normal day)
```powershell
.\scripts\start-all.ps1                      # infra + gateway + core + notification (builds jars if missing; -Build to force)
cd frontend; npm run dev                     # UI on :5173 (proxy -> gateway :18080)
.\scripts\seed-dataset.ps1 -Reset            # 10k customers (stop the services first for a clean load; it also copies notifications to notification-service)
.\scripts\test.ps1                           # all backend tests;  -Service core-service -Tests 'Postgres*' to narrow
.\scripts\stop-all.ps1
```
Debugging in VS Code/IntelliJ: run `CoreServiceApplication`... (main class `com.connectone.ConnectOneApplication`) from the IDE with env `CONNECTONE_DB_PASSWORD`, `CONNECTONE_JWT_SECRET` (same value for all services), after stopping that one service with `stop-all`.
Follow a request: `Select-String logs\*.log -Pattern "<correlation id>"` (the id is in every response header `X-Correlation-Id`).

## STAGING-LIKE workflow (deploy rehearsal)
```powershell
$env:CONNECTONE_DB_PASSWORD = [Environment]::GetEnvironmentVariable('CONNECTONE_DB_PASSWORD','User')   # not printed
docker compose build                         # builds the 4 images (first time ~10 min; layers are cached afterwards)
docker compose up -d                         # postgres, kafka, redis -> core, notification -> gateway -> frontend (ordered by healthchecks)
docker compose ps                            # all "healthy"
docker compose logs -f core-service
```
Release one service with a health gate and automatic rollback:
```powershell
.\scripts\deploy-local.ps1 -Service core-service                       # build tag=<git sha>, roll, health gate, smoke test via the gateway
.\scripts\deploy-local.ps1 -Service core-service -Rollback             # back to the previous tag
.\scripts\deploy-local.ps1 -Service notification-service -SimulateBadRelease   # deliberately broken release: watch the gate fail and roll back
```
This is a rehearsal of the AWS pipeline (docs/DEVOPS_AWS.md): immutable image tag → new revision → wait until healthy → smoke test → rollback.

### Fault-injection exercises (do these, then explain what you saw)
| Do | Expect | Where to look |
|---|---|---|
| `docker stop connectone-kafka` (DEV) or `docker compose stop kafka` | app keeps working; dashboard "outbox waiting" rises; notifications appear after restart | admin dashboard, `logs\core-service.log` (`outbox_relay_degraded`) |
| Stop `notification-service` | payments still work; the notifications screen errors (502 from the gateway); events queue in Kafka and are consumed when it returns | gateway log, Kafka consumer lag |
| Stop Redis | catalogue still served, slightly slower (300 ms connect timeout) | `event=cache_error` |
| `docker compose stop postgres` | readiness goes DOWN; the gateway returns 5xx; recovery is automatic | `/actuator/health/readiness` |
| Send a malformed event to the topic (see DEBUGGING_PLAYBOOK §10) | consumer retries 3×, then the record lands on `connectone.events.v1.DLT` | `kafka-console-consumer` on the DLT |
| Deploy a bad release (`-SimulateBadRelease`) | health gate fails, automatic rollback | script output |

## Test the system as a user and as a tester
- Browser: sign in as `cust00001@connectone.test` (customer), `admin@connectone.test` (admin), `agent01@connectone.test` (support). Password = your `CONNECTONE_SEED_PASSWORD`.
- Manual test plan with charters and expected results: docs/TEST_PLAN.md.
- API requests ready to click (VS Code "REST Client" extension): `api-tests/connectone.http`.
- Automated: backend `scripts\test.ps1`, browser journeys `cd frontend; npm run e2e`, load `node scripts\loadtest.mjs`, **event-flow smoke** `node scripts\smoke-events.mjs`.
