# Known limitations

Honest list of what is simulated, missing, or deliberately imperfect. Nothing here is hidden behind a claim of completeness.

## Simulated by design (replace behind the existing interfaces)

| Concern | Interface | Simulation | Real integration would |
|---|---|---|---|
| Payments | `PaymentProvider` | `SimulatedPaymentProvider` accepts whatever outcome the caller posts to `POST /payments/{id}/confirm` and derives `SIM-<uuid>` | verify a **signed webhook** from the gateway; never trust the browser; call the gateway to create/capture/refund |
| Refunds | `PaymentService.refund` | marks the refund `SUCCEEDED` immediately; no gateway call | create the refund at the gateway, leave `PENDING`, finish on webhook |
| OTP | `OtpProvider` | 6-digit code returned in the API response when `connectone.otp.expose-code=true` | send via SMS/email; never return it |
| Notifications | `NotificationProvider` | delivery "succeeds" (or always fails when `connectone.notification.simulated-failure=true`) | SMS/email/push vendor SDK |
| Streaming | – | catalogue + `playbackUrl` metadata only; `play` records watch history | signed CDN URLs / DRM |

## Not implemented

- **Remaining monolith.** Only notifications have been extracted (docs/MICROSERVICES.md). Payments, booking, subscriptions, support, streaming and identity still live in core-service and share one database.
- **Automatic subscription renewal charging.** `autoRenew` is stored; nothing charges it. Expiry and reminders run in one instance only.
- Password reset / account recovery, e-mail verification, privacy/deletion flows, MFA, rate limiting on public endpoints.
- Multi-instance safety for `SubscriptionExpiryJob` and the notification retry job (the payment-expiry job uses `SKIP LOCKED`).
- Real hosting for video, partner booking inventory, tax/invoicing, multi-currency (INR only).

## Microservices (current state)
- **Kafka** (KRaft, single broker, replication factor 1) is used only for domain events from core to notification-service; no TLS/auth, no schema registry (the contract is `docs/events/*.json` + contract tests).
- **Gateway** has no rate limiting, circuit breaker or authentication of its own (tickets CO-0066, CO-0067); it runs on Boot 4.0.9 because Spring Cloud for Boot 4.1 is only a release candidate.
- **notification-service** verifies JWT signature and expiry only; a token revoked at logout stays valid there until expiry (<= 15 min) (CO-0070).
- **Outbox relay** publishes one record at a time inside a DB transaction; the failure mode is documented and measured (CO-0072).
- **AWS Terraform and the CD workflow describe the pre-extraction topology** (core only). CD is disabled until `ENABLE_CD=true` (CO-0074). Compose, `deploy-local.ps1` and CI cover all services.
- A Redis cache test failed once inside a full multi-module run and passed on re-run (CO-0071); not reproduced in 3 later full runs.
- Docker Desktop is limited to 2 GB by `.wslconfig`: the compose stack fits without observability but cold starts are slow (core-service took 335 s once under contention, ~57 s on an idle VM).

## Redis

- Optional; off by default (`CONNECTONE_CACHE_TYPE=none`). When `redis`, only the **public recharge-plan catalogue** is cached (5 min TTL).
- Fail-open: if Redis is down the API serves from PostgreSQL. There is **no circuit breaker**, so each request still pays the
  300 ms connect timeout while Redis is down (measured: ~330 ms vs 13 ms cached).
- OTP and session state are intentionally **not** in Redis (PostgreSQL is the system of record and is already transactional).

## Performance and data-shape debt (intentional, for training)

On the 10,000-customer dataset some endpoints are measurably slower than they should be, and some list endpoints are unbounded
or capped without paging. Baseline numbers are in DEPLOYMENT_AND_OPERATIONS.md. The causes are not stated here (see tickets
Level 5). Symptoms you will observe: customer 1's recharge history takes ~200 ms p50 under 10 concurrent users; the admin dashboard
~0.8 s; `GET /payments/me` returns every payment for the customer.

## Operational notes

- First requests after a cold JVM start are slow (class loading/JIT): up to several seconds. Not a defect.
- Login uses BCrypt cost 12 (~250 ms CPU each); 10 simultaneous logins on a laptop take ~0.8 s p50.
- Do not run `mvnw compile` while `spring-boot:run` is serving from the same `target/` directory; a rebuild during a run once left
  `target/classes` without resources and broke the next test run until `process-resources` re-ran.
- The dataset's anchor date is "today" at load time; ages drift as days pass. Re-run `seed-dataset.ps1 -Reset` to refresh.
- `PaymentExpiryJob` (every minute) turns `INITIATED` payments older than 30 min **and younger than 7 days** into `EXPIRED`. Older
  stuck payments are left for humans (they appear in the dataset as investigation scenarios).

## Test coverage gaps

- No frontend component tests beyond helpers; UI correctness rests on the browser journey (`npm run e2e`).
- No automated security scanning, no mutation/contract tests, no soak test. Load test is a small in-repo tool, not a capacity study.
