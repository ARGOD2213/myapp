# Testing guide

## Test types and where they run

| Type | Where | Database | Command | Last verified result (2026-11-03) |
|---|---|---|---|---|
| **Unit** (no Spring, no DB) | `CheckoutCouponPricingTest`, `frontend/src/format.test.ts` | none | `scripts\test.ps1 CheckoutCouponPricingTest` · `npm test` | 3 + 4 passed |
| **API integration** (full Spring context + MockMvc) | `ConnectOneApplicationTests` (12 tests) | PostgreSQL `connectone_test` | `scripts\test.ps1 ConnectOneApplicationTests` | 12 passed |
| **PostgreSQL concurrency & constraints** | `PostgresConcurrencyAndConstraintTests` (10 tests) | PostgreSQL `connectone_test` | `scripts\test.ps1 PostgresConcurrency*` | 10 passed |
| **Redis** | `RedisCacheTests` (2) | PostgreSQL + Redis `:6380` DB 15 | `scripts\test.ps1 RedisCacheTests*` | 2 passed (available test skips if Redis is down) |
| **Browser E2E** (real UI → API → PostgreSQL) | `frontend/e2e/journeys.mjs` (23 steps) | `connectone_dev` + dataset | `npm run e2e` | 23/23, 0 server 5xx |
| **Load** | `scripts/loadtest.mjs` | `connectone_dev` + dataset | `node scripts\loadtest.mjs` | see DEPLOYMENT_AND_OPERATIONS.md |

Total backend: **27 tests, 0 failures** (`scripts\test.ps1`). **There are no H2 tests any more**: H2 hid three PostgreSQL-only
production bugs (TAKEOVER_AUDIT F1/F2), so the suite runs on the real engine.

## Test database safety

- Tests use `connectone_test` (same container, separate database). `TestDatabaseConfiguration` **drops and re-migrates the whole schema**
  at every Spring context start and refuses to run unless `spring.datasource.url` is exactly `jdbc:postgresql://localhost:5434/connectone_test`.
- Profile `test` lives in `src/test/resources/application-test.yml` (never on the main classpath). It uses the same `CONNECTONE_DB_PASSWORD`.
- The dev database and `ai-lab-postgres` are never touched by tests. Redis tests use logical DB 15 and key prefix `connectone-test:`.
- `scripts\test.ps1` loads the DB password from your user environment; `mvnw` run directly needs `CONNECTONE_DB_PASSWORD` in the shell.

## What the PostgreSQL tests prove

| Test | Guarantee |
|---|---|
| `concurrentRefundsNeverExceedThePaymentAmount` | 8 simultaneous ₹100 refunds on a ₹299 payment → exactly 2 succeed, total ₹200, the rest 400 |
| `duplicateCallbacksAreAppliedExactlyOnce` | 8 simultaneous identical callbacks → all answer 200, one `payment_callback`, one notification, recharge `SUCCEEDED` once |
| `sameIdempotencyKeyRechargeCreatesOnePaymentUnderConcurrency` | 6 racers with one key → exactly one recharge and one payment row |
| `onlyOneCustomerWinsTheSameSeat` | 10 customers, same seat → one booking, nine 409, one seat claim |
| `couponUsageLimitHoldsUnderConcurrentRedemption` | limit 3, 8 racers → 3 winners, losers fully rolled back |
| `concurrentRenewalsWithDistinctKeysCreateASinglePendingPayment` | subscription row lock serialises renewals |
| `databaseConstraintsRejectInvalidRows` | unique phone, payment parent CHECK, positive amount, status CHECK, FK RESTRICT, one coupon redemption per customer |
| `couponDiscountIsAppliedToThePaymentAndReleasedWhenPaymentFails` | ₹299 − 10 % = ₹269.10; failed payment gives the coupon back |
| `invalidCouponRollsBackTheWholeCheckout` | an invalid coupon leaves no recharge/payment rows |
| `staleInitiatedPaymentsExpireAndReleaseTheirBusinessRecords` | expiry job marks payment and recharge `EXPIRED` |
| Existing 12 | auth/roles/refresh revocation, OTP lockout, recharge→payment→refund→subscription, booking flow, streaming entitlement, ticket isolation, admin RBAC + coupon, seed fixture idempotency, Flyway from empty DB (6 migrations, 16 key tables) |

## Writing a new test

1. Prefer the concurrency-test style: create fixtures with `JdbcTemplate` (`customer()` helper), call the **service** directly, assert on the database.
2. Use `race(List<Callable<Object>>)` to release N threads at the same instant.
3. Never assert on timing; assert on invariants (counts, sums, statuses).
4. Keep tests independent: create fresh customers (random e-mail/phone), don't rely on rows from other tests.

## Browser E2E

Needs: API on 18080 with the large dataset loaded, `npm run dev` on 5173, Chrome (`E2E_BROWSER` to override the path), `E2E_PASSWORD`.
It registers a new customer through the real OTP flow, recharges (success and failure), requests a partial refund, subscribes, plays content,
books and cancels a seat, raises a ticket, then signs in as customer 1 (paginated 445-recharge history), a wrong-password case, and the admin
(dashboard, customer search/360, reconciliation, refund monitor, ticket note, plan/offer/catalogue/staff screens, audit log). It **fails** if any
API call returns 5xx. It writes data (new customer, a reconciliation) — re-seed to reset.

## Performance scenarios (expected outcomes)

`node scripts\loadtest.mjs --users 10 --seconds 12` (needs `LOADTEST_PASSWORD`). Expect zero errors; compare p50/p95 against the table in
DEPLOYMENT_AND_OPERATIONS.md. Scenarios worth running yourself: more users (`--users 30`), Redis on vs off for `/recharge-plans`, before/after an index.

## Coverage gaps
No frontend component tests, no contract tests, no security scanning (KNOWN_LIMITATIONS.md).
