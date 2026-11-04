# Local setup (Windows 11, PowerShell)

Everything below was run on the machine this project was built on. Secrets are **never** printed or committed; scripts read
them from Windows *user* environment variables.

## Prerequisites

| Tool | Version used | Check |
|---|---|---|
| JDK | 25 (Microsoft OpenJDK 25.0.2) | `java -version` |
| Maven | wrapper included (`mvnw.cmd`) | – |
| Node.js / npm | 22.14 / 11 | `node -v` |
| Docker Desktop | any recent | `docker ps` |
| Chrome (for browser E2E only) | any | path in `E2E_BROWSER` |

## 1. Infrastructure (ConnectOne only)

**Never touch `ai-lab-postgres` (port 5433).** ConnectOne owns:

| Container | Port | Purpose |
|---|---|---|
| `connectone-postgres` (postgres:17) | 127.0.0.1:**5434** | databases `connectone_dev` and `connectone_test`, role `connectone_app` |
| `connectone-redis` (redis:7-alpine) | 127.0.0.1:**6380** | optional catalogue cache (a different Redis already listens on 6379 — it is not ours) |

First-time creation (skip if the containers exist; use `docker start connectone-postgres connectone-redis` otherwise):

```powershell
$secure = Read-Host "New local ConnectOne DB password" -AsSecureString
$pw = [System.Net.NetworkCredential]::new("", $secure).Password
[Environment]::SetEnvironmentVariable("CONNECTONE_DB_PASSWORD", $pw, "User")   # persists for new terminals
docker run --name connectone-postgres --restart unless-stopped `
  --env POSTGRES_DB=connectone_dev --env POSTGRES_USER=connectone_app --env "POSTGRES_PASSWORD=$pw" `
  --publish 127.0.0.1:5434:5432 --volume connectone-postgres-data:/var/lib/postgresql/data --detach postgres:17
docker exec connectone-postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE connectone_test"'
docker run -d --name connectone-redis --restart unless-stopped -p 127.0.0.1:6380:6379 redis:7-alpine redis-server --maxmemory 64mb --maxmemory-policy allkeys-lru
```

Other user environment variables (set once, open a new terminal afterwards):

```powershell
[Environment]::SetEnvironmentVariable("CONNECTONE_SEED_PASSWORD", "<12+ chars, your choice>", "User")  # password of every synthetic account
[Environment]::SetEnvironmentVariable("CONNECTONE_JWT_SECRET",   "<random 32+ chars>",           "User")  # optional: else random per run
```
See `.env.example` for the full list (names only).

Convenience for running psql without a password prompt (the container's superuser is `connectone_app`):
```powershell
docker exec -it connectone-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 2. Start the backend

```powershell
.\scripts\start-backend.ps1            # port 18080, Flyway migrates connectone_dev to V6, Hibernate validates
# redis catalogue cache on:
$env:CONNECTONE_CACHE_TYPE = "redis"; .\scripts\start-backend.ps1
# JSON logs:
.\scripts\start-backend.ps1 -Profile json
```
Port **8080 is blocked** on this PC (Windows reserved range), hence 18080. Override with `-Port` or `CONNECTONE_PORT`.
Check: `http://localhost:18080/actuator/health` → `{"status":"UP"}`; `/api/v1/system/status`; `/api/v1/recharge-plans`.

## 3. Load data

```powershell
.\scripts\seed-dataset.ps1 -Reset                 # 10,000 customers (~20 s). Stop the API first for a clean load.
.\scripts\seed-dataset.ps1 -Reset -Customers 500  # small and quick
```
It refuses to run unless the container's database is `connectone_dev`. `-Reset` TRUNCATEs users and all transactional data, then regenerates
(deterministic for a given `-Customers`/`-Base`). It never touches `ai-lab-postgres` or `connectone_test`. Output ends with invariant
checks (all must be 0) and the scenario counts.

Small fixture (3 customers, 2 privileged users) — used by tests and available for quick demos: start the app with
`CONNECTONE_SEED_ENABLED=true` and `CONNECTONE_SEED_PASSWORD` set (idempotent; only allowed on the two ConnectOne databases).

## 4. Frontend

```powershell
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api -> http://localhost:18080 (override: $env:CONNECTONE_API)
npm run build        # type-check + production bundle in frontend/dist
npm test             # vitest (unit)
```
Sign in at `/login`: `cust00001@connectone.test` (customer with a large history), `admin@connectone.test` (back office),
`agent01@connectone.test` (support agent). Password = your `CONNECTONE_SEED_PASSWORD`.

## 5. Tests

```powershell
.\scripts\test.ps1                      # all backend tests (PostgreSQL connectone_test; Redis tests skip if Redis is down)
.\scripts\test.ps1 'PostgresConcurrency*'
cd frontend; npm test                   # frontend unit tests
$env:E2E_PASSWORD = "<seed password>"; npm run e2e   # browser journeys (needs API + dataset + `npm run dev`)
```
Details: TESTING_GUIDE.md.

## 6. Stopping / resetting

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ? { $_.CommandLine -match 'ConnectOneApplication' } | % { Stop-Process -Id $_.ProcessId }
docker stop connectone-redis connectone-postgres      # data persists in the named volume
```
Rebuild the dev database from empty (ConnectOne database only!):
```powershell
docker exec connectone-postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE connectone_dev" -c "CREATE DATABASE connectone_dev"'
.\scripts\start-backend.ps1     # Flyway recreates V1..V6; then run seed-dataset.ps1 -Reset
```

## Troubleshooting quick list
- `Port 8080 was already in use` → use 18080 (default now).
- `invalid value for parameter "TimeZone": "Asia/Calcutta"` → run through the provided scripts/pom (UTC is forced).
- `CONNECTONE_DB_PASSWORD is not set` → set the user env var, open a **new** terminal.
- Tests fail with *missing table* right after a rebuild during a run → `.\mvnw.cmd process-resources`, re-run (see KNOWN_LIMITATIONS).
- More in DEBUGGING_PLAYBOOK.md.
