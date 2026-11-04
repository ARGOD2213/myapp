# API reference

Base URL `http://localhost:18080/api/v1`. JSON everywhere. Auth: `Authorization: Bearer <accessToken>` unless marked **public**.
Every response carries `X-Correlation-Id`; send your own (8–64 chars `[A-Za-z0-9._-]`) to trace a request.

**Errors** (all endpoints):
```json
{ "timestamp": "2026-11-03T17:41:07Z", "status": 409, "error": "Conflict", "category": "CONFLICT",
  "message": "Payment is not awaiting confirmation", "correlationId": "7d3f…" }
```
`category`: `VALIDATION`(400) `AUTHENTICATION`(401) `AUTHORIZATION`(403) `NOT_FOUND`(404) `CONFLICT`(409/422) `INTERNAL`(5xx).

**Pages** (`Page` = Spring Data serialised via DTO): `{ "content": [...], "page": { "size": 20, "number": 0, "totalElements": 52912, "totalPages": 2646 } }`, params `page` (0-based), `size` (max 100 on admin lists).

Create-style calls need an `idempotencyKey` (8–100 chars); replaying the same key with the same payload returns the original result, a different payload returns 409.

## Auth (public)
| Method | Path | Body → Response |
|---|---|---|
| POST | `/auth/register` | `{email,password(≥12, upper+lower+digit),firstName,lastName,phoneNumber}` → `201 {challengeId,message,developmentOtp}` |
| POST | `/auth/verify-otp` | `{challengeId,code(6 digits)}` → `{accessToken,refreshToken,expiresInSeconds,roles}` |
| POST | `/auth/login` | `{email,password}` → same token response. 5 failures lock for 15 min |
| POST | `/auth/refresh` | `{refreshToken}` → new pair (old refresh token is rotated; reuse revokes) |
| POST | `/auth/logout` | `{refreshToken}` → 204 |

```bash
curl -s -X POST localhost:18080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cust00001@connectone.test","password":"<your seed password>"}'
```

## Customer & numbers
| Method | Path | Notes |
|---|---|---|
| GET | `/customers/me` | `{customerId,email,firstName,lastName,phoneNumber,accountStatus,createdAt}` |
| PUT | `/customers/me` | `{firstName,lastName,displayLanguage("en"|"hi"|"en-IN"…)}` → 204 |
| GET | `/customers/me/activity?limit=20` | activity feed |
| GET | `/customers?q=&status=&page=&size=` | **STAFF** (`CUSTOMER_READ`): search name/email/phone → Page |
| GET | `/mobile-numbers` · POST `/mobile-numbers` `{phoneNumber}` | list / add |
| PUT | `/mobile-numbers/{id}/primary` · DELETE `/mobile-numbers/{id}` | make primary / disconnect (409 while a recharge is in flight) |

## Recharge
| Method | Path | Notes |
|---|---|---|
| GET | `/recharge-plans?query=&maxPrice=&maxValidityDays=&minDataAllowanceMb=` | **public**, cached when Redis is on |
| POST | `/recharges` | `{mobileNumberId,planId,idempotencyKey,couponCode?}` → `201 {id,paymentId,mobileNumberId,planCode,amount,status:"PENDING",…}` |
| GET | `/recharges/me?page=&size=` | Page |

## Payments & refunds
| Method | Path | Notes |
|---|---|---|
| GET | `/payments/me` | **unpaged** array `{id,amount,currency,status,providerReference,createdAt}` |
| POST | `/payments/{id}/confirm` | `{callbackId(8–100),outcome:"SUCCEEDED"|"FAILED"}` (owner only). Same `callbackId` again → same result, applied once. Different outcome with same callback → 409 |
| POST | `/payments/{id}/refunds` | `{amount,reason,idempotencyKey}` → `201`. Cumulative refunds may not exceed the amount (400); only `SUCCEEDED` payments (409). Full refund reverses recharge / cancels subscription / releases booking |
| GET | `/refunds/me` | own refunds with `paymentAmount` |
| POST | `/coupons/validate` | `{code,amount}` → `{couponCode,discountPercent,discountAmount,payable}` (no side effects) |
| POST | `/coupons/redeem` | `{code}` legacy standalone redemption |
| GET | `/payments?status=` | **STAFF** (`PAYMENT_MONITOR`), latest 200, no paging (legacy) |

```bash
# initiate then pay
curl -s -X POST localhost:18080/api/v1/recharges -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -d '{"mobileNumberId":1,"planId":2,"idempotencyKey":"demo-0001-abc"}'
curl -s -X POST localhost:18080/api/v1/payments/52913/confirm -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -d '{"callbackId":"cb-demo-0001","outcome":"SUCCEEDED"}'
```

## Subscriptions
GET `/subscriptions/plans` · POST `/subscriptions` `{planId,autoRenew,idempotencyKey,couponCode?}` → `{id,paymentId,planCode,planName,status:"PENDING",…}` ·
POST `/subscriptions/{id}/renew` `{idempotencyKey}` · POST `/subscriptions/{id}/cancel` · GET `/subscriptions/{id}/entitlement` · GET `/subscriptions/me`.

## Booking
GET `/bookings/catalogue?type=&query=&location=` (upcoming, available, ≤200) · GET `/bookings/catalogue/{id}/availability` (array of free seat numbers as strings) ·
POST `/bookings` `{catalogItemId,seatOrSlot("1".."capacity"),idempotencyKey,couponCode?}` → `201` booking with `paymentId` (seat is claimed immediately; 409 if taken) ·
GET `/bookings/me` · POST `/bookings/{id}/cancel` (PENDING → cancelled & seat freed; CONFIRMED → full refund).

## Streaming
GET `/streaming/content?category=&q=&type=` · GET `/streaming/content/{id}` · POST `/streaming/content/{id}/play?progressSeconds=0` (403 without an active in-window subscription covering the category) · GET `/streaming/history/me`.
Admin: POST/PUT `/streaming/admin/content[/{id}]` (full `ContentRequest`).

## Support
POST `/support/tickets` `{subject,description,category,priority}` · GET `/support/tickets/me` · GET `/support/tickets/{id}` → `{ticket,comments}` (internal notes only for staff) ·
GET `/support/tickets/{id}/history` · POST `/support/tickets/{id}/comments` `{body,internal}` ·
**STAFF:** GET `/support/tickets?status=&priority=&category=&page=&size=&sort=` (array) · PUT `/support/tickets/{id}/assignment` `{agentUserId}` · PUT `/support/tickets/{id}/status` `{status}`.
Transitions: `OPEN→IN_PROGRESS|WAITING`, `IN_PROGRESS→WAITING|RESOLVED`, `WAITING→IN_PROGRESS|RESOLVED`, `RESOLVED→IN_PROGRESS|CLOSED` (others 409).

## Notifications
GET `/notifications/me` (latest 200) · POST `/notifications/{id}/read` → 204.

## Admin (`ADMIN` with all five permissions unless noted)
| Path | Purpose |
|---|---|
| GET `/admin/dashboard` | counts by status, revenue/refunds 30d, reconciliation count, stale tickets |
| GET `/admin/customers/{id}` | customer 360 (STAFF with `CUSTOMER_READ`) |
| PUT `/admin/customers/{id}/status` `{status}` | ACTIVE/SUSPENDED/CLOSED |
| GET `/admin/payments?status=&reconciliation=&q=&customerId=&page=&size=` | payment monitor (STAFF with `PAYMENT_MONITOR`); `q` = payment id or provider-reference prefix |
| POST `/admin/payments/{id}/reconcile` `{outcome,note?}` | resolve `RECONCILIATION_REQUIRED/RETRY_REQUIRED` (recharge & booking payments; audited) |
| GET `/admin/refunds?status=` · GET `/admin/notification-deliveries?status=` | monitors (STAFF) |
| GET/POST/PUT `/admin/recharge-plans[/{id}]`, `/admin/subscription-plans[/{id}]` | plan management |
| GET/POST `/admin/offers`, POST `/admin/offers/{id}/coupons`, PUT `/admin/offers/{id}/active`, PUT `/admin/coupons/{id}/active` | promotions |
| GET/POST `/admin/support-agents`, PUT `/admin/support-agents/{userId}/status` | agents |
| GET `/admin/roles`, GET `/admin/permissions`, POST `/admin/roles/{id}/permissions` `{permissionId}`, POST `/admin/users/{id}/roles` `{roleId}` | RBAC |
| GET/POST/PUT `/admin/booking-items[/{id}]` · GET `/admin/bookings` | schedules, recent bookings |
| GET/PUT `/admin/streaming-content[/{id}]` | content visibility/title |
| GET `/admin/audit?page=&size=` · POST `/admin/notifications/retry` | audit trail (array), force retry |

## Operations
GET `/api/v1/system/status` (public) · GET `/actuator/health` (public; `/health/liveness`, `/health/readiness`) · ADMIN: `/actuator/metrics[/{name}]`, `/loggers`, `/threaddump`, `/flyway`, `/info`.
Business counters: `connectone.payment.confirmed{outcome}`, `connectone.refund.created`, `connectone.payment.expired`, `connectone.payment.reconciled{outcome}`, `connectone.coupon.redeemed|released`.
