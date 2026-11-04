# Takeover audit

Performed on 2026-11-03 by inspecting the repository, running the build and tests, and querying the real ConnectOne
PostgreSQL instance (`connectone-postgres`, port 5434). The previous model's completion report was **not** trusted; every
row below states what was actually observed. Nothing had been committed (`git log` empty, all files untracked).

## What the repository really contained

| Claim in the handover | Reality found |
|---|---|
| "Hibernate schema validation" | Only **one** JPA entity exists (`RechargePlan`). All other modules use `JdbcTemplate` with hand-written SQL, so `ddl-auto: validate` validates almost nothing. The architecture is *JDBC-first*; it was kept (see ARCHITECTURE.md). |
| "12 passing tests" | True, but they ran on **H2 in MSSQL mode**. Against PostgreSQL 17, 3 of the code paths crashed (below). |
| Default run configuration | `application.yml` defaulted to an H2 *file* database; PostgreSQL required a `postgres` profile. Switched: PostgreSQL is now the default; H2 was removed entirely. |
| Flyway V1–V5 | Applied and valid on PostgreSQL (36 tables). **V2 and V3 are empty files** (0 bytes). They are already applied, so they cannot be edited; they stay as historical placeholders. `TokenService.java` and `RechargeRepository.java` are also empty files (dead, left untouched, listed as tickets). |
| API on port 18080 | Port 8080 is blocked on this machine at OS level (no listener, bind fails). 18080 is the standard dev port. A stale ConnectOne JVM from the earlier session was still running on 18080 and was stopped. |
| Frontend | **Did not exist** (`static/` and `templates/` empty). |
| Redis / Kafka | Not present. A local `redis-server` process owns port 6379 and is **not** ConnectOne's; it was left alone. |

## Defects found and fixed during takeover

| # | Defect | Evidence | Fix |
|---|---|---|---|
| F1 | **Booking cancellation crashed on PostgreSQL** (`FOR UPDATE cannot be applied to the nullable side of an outer join`). H2 accepted it. Every `POST /bookings/{id}/cancel` would have returned 500. | Integration test failed on PostgreSQL only | `FOR UPDATE OF b` in `BookingService.queryBooking` |
| F2 | **Null-typed `lower(?)` parameters failed on PostgreSQL** (`function lower(bytea) does not exist`) in recharge-plan search, booking catalogue, streaming catalogue and support-ticket filters. | Test `listsAndFiltersActiveRechargePlans…` failed | `CAST(? AS VARCHAR)` / `cast(:query as string)` |
| F3 | No catch-all exception handler: unexpected exceptions fell through to Spring's default `/error` body, and malformed requests/auth errors had inconsistent shapes. | Code review | `ApiExceptionHandler` rewritten (category + correlation id, 4xx pass-through, 401/403 re-thrown to the security chain) |
| F4 | `payment_record`, `recharge_transaction` could not represent processing/expired/cancelled/reconciliation states required by the product. | Schema review | Migration **V6** (additive) |
| F5 | Coupons could be "redeemed" but never affected a payment total (`KNOWN_LIMITATIONS` of the old docs said so). | Code review | `CheckoutCouponService`, wired into recharge/booking/subscription checkout |
| F6 | Redis catalogue cache (my own addition) used `@CacheEvict(allEntries=true)`; `Cache.clear()` is not immediate for Redis, so admin edits would have served a stale catalogue for up to the TTL. Caught by an integration test before it shipped. | `RedisCacheTests` | `PlanCatalogueCache`: after-commit `invalidate()` |
| F7 | My first Redis test wrote the *test* database's catalogue into the dev API's Redis keyspace. | Live API returned 3 plans | Tests use Redis logical DB 15 and key prefix `connectone-test:` |
| F8 | Boot's Redis health indicator would flip overall `/actuator/health` to 503 when Redis is down. | Observed 503 with Redis stopped | Own fail-open `cache` indicator; Redis indicator disabled |

## Known defects deliberately **not** fixed

These are genuine weaknesses that make good training material. They are recorded with root causes in
[`_ANSWER_KEY.md`](_ANSWER_KEY.md) — **do not open that file until you have attempted the related tickets.**
Each is also listed in KNOWN_LIMITATIONS.md without the cause (e.g. "some list endpoints are unpaged",
"some queries are slow on the large dataset").

## Module status

Legend: **Complete** = works end to end and is covered by a test run against PostgreSQL; **Partial** = works with the gaps named.

| Module | Existing implementation | Verified behaviour | Defects / gaps | Required work (done?) |
|---|---|---|---|---|
| Identity & auth | Register, OTP verify, login, refresh rotation, logout, JWT (15 min) + refresh (30 d), lockout after 5 failures (15 min), OTP 5 attempts/5 min | Tests: lockout, OTP limit, token revocation, refresh-reuse detection (PostgreSQL) + live browser login/register/logout | OTP stored in DB (acceptable); `developmentOtp` echoed in dev | **Complete** (providers extracted behind `OtpProvider`) |
| Customer | Profile, activity, admin search | Search paged 15/page, 10k customers; **profile update added** (`PUT /customers/me`) | `LIKE '%q%'` search is unindexed (slow at scale) | **Complete**; search perf = training ticket |
| Mobile numbers | List/add | Add/primary/disconnect verified in browser; disconnect blocked while a recharge is in flight | – | **Complete** (primary/disconnect added) |
| Recharge | Plans, filters, initiate, history (paged) | Idempotent initiate; coupon discount; PostgreSQL-tested concurrency on duplicate keys | History query scans `payment_record` (no FK index) | **Complete**; perf = training ticket |
| Payment | Simulated provider, confirm callback, refunds (partial/full), idempotency, notifications | 8-thread duplicate-callback test → exactly one application; 8-thread refund race → never exceeds amount | Customer may refund own payment with no approval step (business gap, ticket); provider trusts caller (simulated by design) | **Complete**; **PaymentExpiryJob + admin reconciliation added** |
| Subscription | Purchase, renew, cancel, expiry job, entitlement | Concurrent renewals → single pending payment | `renew` activates an EXPIRED subscription before payment (see answer key); auto-renewal is stored but not charged | **Partial** |
| Booking | Catalogue, availability, seat claims, create, cancel/refund | 10-thread same-seat race → exactly one winner; F1 fixed | No cancellation-window policy | **Complete** (policy gap = ticket) |
| Streaming | Catalogue, entitlement check on play, history | 403 without entitlement; history recorded | Metadata only (no video) by design | **Complete** (simulated) |
| Support | Tickets, comments (internal/public), assignment, transitions, history | Customer isolation, transition table enforced | Search returns an array without a total | **Complete** |
| Notifications | Enqueue, history (last 200), read, retry (≤5, 1 min backoff) | Duplicate events deduped by idempotency key | Constant backoff; list capped, unpaged | **Complete** (simulated delivery) |
| Admin | Plans, offers/coupons, agents, roles, audit, bookings | **Added:** dashboard, customer 360, payment/refund/notification monitors, reconciliation, booking-item & streaming content management | – | **Complete** |
| Frontend | none | 23-step browser journey passes (see TESTING_GUIDE) | – | **Built** |
| Data | 3 demo customers | **10,000-customer deterministic dataset**, 15–23 s load | – | **Built** |
| Observability | health, info | correlation ids, access/audit logs, metrics, slow-query logging | – | **Built** |
| Kafka | none | – | Not added (justification in KNOWN_LIMITATIONS) | **Not implemented, by decision** |
