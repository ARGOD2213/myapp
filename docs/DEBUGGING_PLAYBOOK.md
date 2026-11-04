# Debugging playbook (Windows / PowerShell)

General method: **reproduce → find the correlation id → read the log lines of that request → read the SQL / rows → form one hypothesis → change one thing → prove with a test.**
Never guess from a stack trace alone; never "fix" in the browser what is wrong in the database.

## 0. Tools you have

```powershell
# API process / logs (if started by scripts\start-backend.ps1 in a console, the logs are in that console; redirect with Start-Process -RedirectStandardOutput)
# Log line format:  2026-11-03T22:50:12.345+05:30  INFO [<correlationId>] [u=<userId>] connectone.access : method=POST path=/api/v1/recharges status=201 durationMs=42
# JSON logs: .\scripts\start-backend.ps1 -Profile json

# psql shortcut
function cq { param($sql) docker exec connectone-postgres sh -c "psql -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -c `"$sql`"" }
cq "select count(*) from payment_record"
```
Every response has `X-Correlation-Id`; the UI shows it as **Reference** under error banners; audit rows store it (`audit_event.correlation_id`).

## 1. HTTP 500 errors
1. Get the id: `curl -i …` → `X-Correlation-Id`, or the UI's "Reference".
2. `Select-String -Path <logfile> -Pattern "<id>"` — the unhandled exception is logged once as `event=unhandled_exception type=… ` with the stack trace (the client only saw "An unexpected error occurred").
3. If the type is `DataAccessException`/`PSQLException`, copy the SQL state; check the query in the service; run it in psql with real parameters.
4. Reproduce with a failing test **before** fixing. Remember H2 is gone: if it fails in psql it is a real bug.
5. `GET /actuator/metrics/http.server.requests?tag=status:500` (admin token) shows the rate.

## 2. Slow APIs
1. Access log: `durationMs=` per request. Sort: `Select-String access.log -Pattern "durationMs=\d{4,}"`.
2. `GET /actuator/metrics/http.server.requests?tag=uri:/api/v1/recharges/me` (count, total time, max) with an ADMIN bearer token.
3. Is it DB time? Check PostgreSQL's own record (section 7). Is it the first call after a restart? (cold JVM — ignore.) Is it BCrypt (login)? (~250 ms CPU by design.)
4. Reproduce under load: `node scripts\loadtest.mjs --users 20`. Compare p50/p95.
5. Look for N+1: many near-identical statements per request → `select calls, query from pg_stat_statements order by calls desc limit 10`.

## 3. High CPU
```powershell
Get-Process java | Select Id,CPU,WorkingSet64
jcmd <pid> Thread.print > threads.txt          # or: GET /actuator/threaddump (admin)
```
Find the busiest threads (`top -H` is not on Windows: take 3 thread dumps 5 s apart and look for the same RUNNABLE frames). BCrypt (`org.springframework.security.crypto.bcrypt`) during login storms is normal;
a tight loop in your code or a scheduled job (`PaymentExpiryJob`, `SubscriptionExpiryJob`) is not. `jcmd <pid> GC.heap_info` for GC pressure.

## 4. High memory
```powershell
jcmd <pid> GC.heap_info ; jcmd <pid> GC.class_histogram | Select -First 20
jcmd <pid> GC.heap_dump C:\temp\heap.hprof     # analyse with VisualVM / Eclipse MAT
```
Typical causes here: unbounded result lists (e.g. endpoints returning whole histories), large `queryForList` calls. Compare against `/actuator/metrics/jvm.memory.used`.

## 5. Database connection exhaustion
Symptoms: requests hang ~5 s then 500 with `HikariPool-… Connection is not available, request timed out after 5000ms`.
```
GET /actuator/metrics/hikaricp.connections.active   (max = 10)   |  hikaricp.connections.pending   |  hikaricp.connections.acquire
```
```sql
select state, count(*) from pg_stat_activity where datname='connectone_dev' group by 1;
select pid, now()-xact_start as tx_age, state, left(query,80) from pg_stat_activity where datname='connectone_dev' and state<>'idle' order by tx_age desc;
```
`idle in transaction` rows = a transaction held open (e.g. a slow external call inside `@Transactional`). Hikari's leak detector logs `Apparent connection leak` after 20 s with the stack that borrowed it.

## 6. Database locks
```sql
select pid, pg_blocking_pids(pid) as blocked_by, wait_event_type, now()-query_start as waiting, left(query,100)
from pg_stat_activity where cardinality(pg_blocking_pids(pid)) > 0;
select * from pg_locks where not granted;
```
In ConnectOne locks come from `SELECT … FOR UPDATE` on payment/subscription/booking/coupon/customer rows. Lock waits > 1 s are logged by PostgreSQL (`log_lock_waits=on`). To free a stuck session (your own dev DB only): `select pg_terminate_backend(<pid>)`.

## 7. Slow SQL
Already enabled on the ConnectOne instance: `log_min_duration_statement = 250 ms`, `pg_stat_statements` extension in `connectone_dev`.
```powershell
docker logs --since 10m connectone-postgres 2>&1 | Select-String "duration:"
```
```sql
select calls, round(mean_exec_time::numeric,1) as mean_ms, round(total_exec_time::numeric) as total_ms, left(query,100) from pg_stat_statements order by total_exec_time desc limit 10;
select pg_stat_statements_reset();     -- before an experiment
explain (analyze, buffers) <query>;    -- look for Seq Scan with large "Rows Removed by Filter", and estimate vs actual row mismatches
```
Check `select relname, seq_scan, idx_scan, n_live_tup from pg_stat_user_tables order by seq_scan desc;` for tables read by scanning.

## 8. Failed Flyway migrations
Startup fails with `FlywayException`/`Validate failed: Migration checksum mismatch` (someone edited an applied migration) or `Detected failed migration`.
```sql
select installed_rank, version, description, success, checksum from flyway_schema_history order by installed_rank;
```
Rules: never edit an applied `V#`; add `V7__…sql`. A failed migration leaves a `success=false` row: fix the SQL, remove that row **only in your dev DB** (`delete from flyway_schema_history where success=false`), and re-run; V6 style DDL is transactional in PostgreSQL so a failure rolls back cleanly.
Fresh-start check: drop/recreate `connectone_dev` (LOCAL_SETUP.md §6) and boot.

## 9. Redis outages
`docker stop connectone-redis` while the API runs with `CONNECTONE_CACHE_TYPE=redis`: API keeps working (fail-open), logs `event=cache_error op=get cache=recharge-plans cause=RedisConnectionFailureException`, `/actuator/health` stays UP with `cache.redis = unreachable…` (admin view). Latency rises by ~300 ms/request to the cached endpoint (connect timeout, no circuit breaker).
Inspect: `docker exec connectone-redis redis-cli -n 0 --scan --pattern "connectone:*"` · `ttl <key>` · `flushdb` (dev only; **not** on 6379, which isn't ours).

## 10. Kafka consumer failures
Not applicable — Kafka is not part of ConnectOne (see KNOWN_LIMITATIONS.md for why). The equivalent for the notification queue: `select status, count(*), max(attempt_count) from notification_record group by 1;` and `select id, last_error, next_attempt_at from notification_record where status='FAILED' order by id desc limit 20;`, retry via `POST /admin/notifications/retry`.

## 11. Failed payments
```sql
select id, status, reconciliation_state, failure_reason, attempt_count, provider_reference, created_at from payment_record where id = <id>;
select * from payment_callback where payment_id = <id>;
select r.status as recharge_status from recharge_transaction r join payment_record p on p.recharge_id = r.id where p.id = <id>;
select * from refund_record where payment_id = <id>;
select event_type, status, attempt_count, last_error from notification_record where idempotency_key like 'payment-<id>-%';
```
Compare payment vs business-record status (they must agree). Use the admin screen *Payments → filter `RECONCILIATION_REQUIRED`* to resolve stuck ones. Check logs: `Select-String <log> "payments/<id>"`.

## 12. Duplicate transactions
```sql
-- same recharge charged more than once
select recharge_id, count(*), sum(amount) from payment_record where status='SUCCEEDED' and recharge_id is not null group by 1 having count(*)>1;
-- same customer, same plan, within a minute
select customer_id, plan_id, count(*) from recharge_transaction where created_at > now() - interval '1 day' group by 1,2 having count(*) > 3 order by 3 desc;
```
Then ask: did the client reuse its `idempotencyKey`? Is `idempotency_key` unique at the table level for that path? (`payment_record.idempotency_key` is; multiple payment rows per recharge are still *possible* — find out why.)

## 13. Invalid authorization (401/403)
1. Decode the token's claims: `$p = $token.Split('.')[1]; [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p.PadRight($p.Length + (4 - $p.Length % 4) % 4,'=')))` → `roles`, `permissions`, `exp`.
2. 401 = missing/expired/revoked token (`category: AUTHENTICATION`); 403 = authenticated but role/permission missing (`AUTHORIZATION`).
3. Controllers use `@PreAuthorize("hasRole(..) and hasAuthority(..)")` — both must match; permissions come from `role_permission`.
4. Suspected revoked token: `select * from access_token_revocation where user_id = <id> order by revoked_at desc limit 5`.

## 14. Frontend ↔ API communication errors
- Browser devtools → Network: failing call, status, response body (`message`, `correlationId`). `ApiError(0, "Cannot reach the ConnectOne API")` means the proxy target is down or wrong (`CONNECTONE_API`).
- 401 loops: `localStorage["connectone.session"]` holds the tokens; clear it to reset. The client refreshes once on 401 then signs out.
- Date shown a day off: the API mixes zoned and zone-less timestamps; `frontend/src/format.ts` treats zone-less as UTC.
- CORS errors should not occur (the Vite proxy is same-origin). If you see one, you bypassed the proxy.
- Reproduce without the UI: `curl` the same request with the same token before blaming the frontend.
