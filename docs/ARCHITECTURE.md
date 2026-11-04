# Architecture

## Style: modular monolith, JDBC-first

One deployable Spring Boot application (Java 25, Spring Boot 4.1.2). Modules are packages under `com.connectone`:

```
admin  booking  customer  identity  notification  payment  streaming  subscription  support  telecom  common
```

Each module follows `controller → service → (repository | JdbcTemplate)` with `dto` records for request/response shapes.
Only `telecom` has a JPA entity (`RechargePlan`, via `RechargePlanRepository`) and `customer` has a hand-written
`CustomerRepository`; **every other module issues SQL directly through `JdbcTemplate`** inside `@Transactional` service methods.
This is a conscious property of the codebase you inherited: SQL is explicit, locking is explicit (`SELECT … FOR UPDATE`), and
Hibernate's `ddl-auto: validate` therefore protects only the one mapped entity. Flyway is the schema authority.

### Dependency direction

```
controller ──► service ──► JdbcTemplate / repository
                 │
                 ├──► common.audit (AuditService)      ──► audit_event + connectone.audit log line
                 ├──► notification.NotificationService ──► notification_record (idempotent enqueue)
                 ├──► admin.CheckoutCouponService      ──► coupon rules at checkout
                 └──► ports: PaymentProvider, OtpProvider, NotificationProvider (simulated implementations)
```

Cross-module calls go through services, but modules share one database and some services read other modules' tables
(e.g. `PaymentService` updates recharge/booking/subscription rows). That coupling is real and is part of what you will learn to
navigate. `common` depends on nothing in the business modules.

## Packages in `common`

| Package | Purpose |
|---|---|
| `config` | `SecurityConfiguration`, `JwtAuthenticationFilter`, `JwtTokenService`, `ConnectOnePrincipal`, `DevelopmentSeedRunner` (small opt-in fixture) |
| `web` | `CorrelationIdFilter` (request id + access log), `SystemStatusController` |
| `exception` | `ApiException`, `ApiExceptionHandler` (the single error contract) |
| `audit` | `AuditService` |
| `metrics` | `BusinessMetrics` (Micrometer counters `connectone.*`) |
| `cache` | `CacheConfiguration` (fail-open), `PlanCatalogueCache` |
| `database` | `JdbcRowSupport` (snake_case → camelCase maps), `GeneratedKeySupport` |

## Request lifecycle

1. `CorrelationIdFilter` (highest precedence) accepts or generates `X-Correlation-Id`, puts it in the MDC, echoes it on the response.
2. Spring Security filter chain: `JwtAuthenticationFilter` validates the bearer token (signature, expiry, revocation list in
   `access_token_revocation`), builds `ConnectOnePrincipal(userId,email,roles,permissions)` and puts `userId` in the MDC.
3. Authorization: `authorizeHttpRequests` (public: `/api/v1/auth/**`, `/api/v1/recharge-plans`, `/api/v1/system/status`,
   `/actuator/health/**`; `/actuator/**` = ADMIN) then method security `@PreAuthorize` on controllers.
4. Controller binds/validates the DTO (`@Valid`), calls one service method.
5. Service runs one `@Transactional` unit of work: reads, locks, writes, enqueues notifications, writes audit.
6. Response, or an exception → `ApiExceptionHandler` → `{timestamp,status,error,category,message,correlationId}`.
7. `CorrelationIdFilter` logs `method= path= status= durationMs=` on logger `connectone.access` and clears the MDC.

## Security

- Passwords: BCrypt cost 12. Access JWT: 15 minutes (HS256, secret `CONNECTONE_JWT_SECRET`; a random per-run secret is generated
  if unset, so tokens die on restart). Refresh tokens: random, stored hashed (`refresh_token.token_hash`), rotated on use; reuse of a
  rotated token revokes the family. Logout revokes the access token id.
- Lockout: 5 failed logins → `LOCKED` for 15 minutes. OTP: 5 attempts, 5 minute expiry.
- CSRF disabled (stateless bearer tokens, no cookies). CORS is not enabled: the SPA uses the Vite/same-origin proxy.
- Actuator: only `health` is public; everything else requires `ADMIN`. Health details only for `ADMIN`.
- Never logged: passwords, OTPs, tokens, request bodies, query strings, headers.

## Transactions and concurrency

- Services are `@Transactional`; read-only endpoints use `readOnly = true`.
- Concurrency is handled **in the database**, not in Java: `SELECT … FOR UPDATE` on the payment/subscription/booking row, unique
  indexes (`booking_seat_claim` PK `(catalog_item_id, seat_or_slot)`, idempotency keys, `coupon_redemption (coupon_id, customer_id)`),
  and guarded updates (`… WHERE status = 'PENDING'` then check the row count).
- Optimistic `version` columns exist on subscriptions, bookings and tickets and are bumped on change.
- Idempotency: create-style calls take a client `idempotencyKey`; payment callbacks use a unique `callback_id`.
- `PaymentExpiryJob` uses `FOR UPDATE SKIP LOCKED` so several instances would not double-process.

## Scheduling

`SubscriptionExpiryJob` (60 s): expiry + reminders · `NotificationService` retry (60 s) · `PaymentExpiryJob` (60 s).

## Providers (ports)

`PaymentProvider`, `OtpProvider`, `NotificationProvider` are interfaces with `Simulated*` implementations; see KNOWN_LIMITATIONS.md.

## Frontend

`frontend/` — React 18 + TypeScript + Vite. `src/api.ts` is the only place that calls `fetch` (bearer header, single-flight refresh on 401,
`X-Correlation-Id`, error contract → `ApiError`). Pages live in `src/customer/*` and `src/admin/*`; `App.tsx` defines routes with
`RequireAuth` / `RequireStaff` / `RequireAdmin` guards. No fake data: every screen reads the real API.

## Caching

Optional Redis cache for `RechargePlanService.searchAvailable` only (`@Cacheable`), evicted after commit on admin plan edits.
