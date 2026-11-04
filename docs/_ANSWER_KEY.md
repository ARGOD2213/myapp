# ⚠ ANSWER KEY — do not read until you have submitted your own report for the ticket

Root causes behind the training tickets. "Verified" means I reproduced it against the running system on 2026-11-03; "code" means identified by
reading the code/schema but not exercised at runtime. Where a ticket's first guess would be wrong, that is noted. Dataset ids are examples.

## Level 2 — production support (the anomalies are in the data)

All are produced deterministically by `scripts/dataset/large_dataset.sql` ("controlled investigation scenarios" section); the schema permits every one of them.

| Ticket | What is in the data | How to find it |
|---|---|---|
| CO-0015 duplicate charges | For recharges with `id % 600 = 1`, extra `payment_record` rows were inserted: one `SUCCEEDED` duplicate (attempt 2, 40 s later, different idempotency key) and one `FAILED` (attempt 3, reason `DUPLICATE_SUBMISSION`). **No unique constraint on `payment_record.recharge_id`**, and recharge history INNER JOINs payments, so such recharges appear once per payment row. | `SELECT recharge_id, count(*), sum(amount) FROM payment_record WHERE status='SUCCEEDED' GROUP BY 1 HAVING count(*)>1` (≈43 recharges) |
| CO-0016 paid but pending | (a) `recharge SUCCEEDED / payment FAILED` (company delivered, customer not charged); (b) `recharge PENDING / payment SUCCEEDED` (customer charged, no validity); (c) `recharge PROCESSING` with payment `PROCESSING` + `RECONCILIATION_REQUIRED` | join `recharge_transaction`↔`payment_record`, compare statuses |
| CO-0017 stuck PROCESSING | ≈46 payments `PROCESSING`, `reconciliation_state='RECONCILIATION_REQUIRED'`, with a `payment_callback` row (`ds-cb-<id>`, outcome SUCCEEDED) received minutes after creation but never applied; created 4+ days ago. ≈ plus `RETRY_REQUIRED` failed payments. Resolve via `POST /admin/payments/{id}/reconcile`. | `payment_callback` joined to payments in `PROCESSING` |
| CO-0018 customer 7777 | The generator re-assigned 400 recharges (and their payments) to customer 7777 within one day (3 days ago), spread by `id % 1440` minutes, mixed statuses. It is synthetic velocity, not fraud. Expect a good answer to say "cannot distinguish from abuse without device/IP data, which the system does not record". | group by hour |
| CO-0019 stale tickets | `IN_PROGRESS` tickets older than 40 days force-reset (`id % 40 = 3`) plus natural ones; URGENT unassigned tickets (`id % 200 = 8`). Several hundred tickets are IN_PROGRESS > 30 days. | `support_ticket` by `updated_at`, `assigned_user_id` |
| CO-0020 no notification | `id % 50 = 7` payments never got a notification row (≈900); ≈3 % of notifications `FAILED`, ≈4 % `PENDING` with a past `next_attempt_at`. | anti-join on `idempotency_key = 'payment-<id>-<OUTCOME>'` using `customer_id` (uses the unique index) |
| CO-0021 cancelled but captured | Bookings with `id % 150 = 9` that were CANCELLED had their payment flipped to SUCCEEDED, no refund row (≈43). | `booking_record CANCELLED` + payment `SUCCEEDED`, no `refund_record` |
| CO-0022 subscription vs payment | `ACTIVE` subscriptions with `id % 250 = 11` set back to `PENDING` (paid but not activated) and `id % 250 = 12` whose first payment was set `FAILED` (active but unpaid). Play is allowed only for `ACTIVE` + in-window + entitled category. | join subscription↔payments |
| CO-0023 refund errors | 400 "exceeds remaining balance", 409 "Only successful payments can be refunded", 409 "Idempotency key already used…", 404 for another customer's payment. | `PaymentService.refund` |
| CO-0025 locked accounts | `app_user`: `LOCKED` (c%500=0, `locked_until` tomorrow), `PENDING` (c%300=0, never verified OTP), `DISABLED` (c%997=0). Profile status is a **separate** column. | `app_user` vs `customer_profile` |

## Level 3 — bugs

| Ticket | Root cause | Status |
|---|---|---|
| CO-0027 | `AuthService.register` and add-mobile compare the raw string; the validation regex allows an optional `+`, nothing normalises. `+91…` and `91…` are different strings, so both unique checks pass. | **Verified**: both registrations returned 201 |
| CO-0028 | `SubscriptionService.renew` runs `UPDATE customer_subscription SET status='ACTIVE' … WHERE status='EXPIRED'` **before** the payment exists. | **Verified**: status ACTIVE right after renew. NOTE the first-guess "free access" is **false**: entitlement stays `false` because `expires_at` is still in the past — the bug is the false ACTIVE state (counts, UI, events). |
| CO-0029 | Two code paths insert into `coupon_redemption`: `AdminService.redeem` (standalone, no payment) and `CheckoutCouponService.applyToPayment`. Unique `(coupon_id, customer_id)` makes the second fail with 409. | **Verified**: redeem 200, then checkout 409 |
| CO-0030 | `BookingService.cancel` never compares `starts_at` with now; confirmed → full refund always. | code; the dataset has ≈40 cancelled-after-start bookings |
| CO-0031 | `SupportService.search` returns a `List`, no count query. | code |
| CO-0032 | `PaymentService.monitor` hard-codes `LIMIT 200`. | code |
| CO-0033 | `NotificationService` sets `next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1' MINUTE` for every failure; `attempt_count < 5` is the only bound; the 5th failure leaves `status='FAILED'`, indistinguishable from "will retry". | code |
| CO-0034 | Two sources of truth: `app_user.account_status` and `customer_profile.account_status`. Recharge/booking/streaming use `activeCustomerId` (checks profile `ACTIVE`); `PaymentService.customerId` does not. | **Verified**: customer 33 (profile SUSPENDED, login ACTIVE) — refund **201**, mobile-number list **403**. 353 such customers in the dataset. (The admin API sets both columns, so it is mostly the generator that created the mismatch — the design allows it.) |
| CO-0035 | `JwtTokenService.validateAccessToken` joins `access_token_revocation` **and** `app_user`, so account status is re-checked on **every request**. Expected finding: **no gap**; cost = one extra DB query per request. | **Verified**: after `SUSPENDED`, an existing token gets 401 immediately and fresh login fails |
| CO-0036 | `CustomerRepository.search` builds `"%" + term + "%"` without escaping `%`/`_`. | code |
| CO-0037 | `coupon.coupon_code VARCHAR(40) UNIQUE` is case-sensitive and lookups use `=`. | code (V5) |
| CO-0038 | `OfferRequest` has no `@Future`; DB only enforces `ends_at > starts_at`. | code |

## Level 5 — performance (numbers measured on the 10k dataset, PostgreSQL 17, laptop, 2026-11-03)

| Ticket | Root cause | Evidence |
|---|---|---|
| CO-0051 | `payment_record.recharge_id` (also `booking_id`, `subscription_id`) has **no index** (PostgreSQL does not index FKs automatically). `RechargeService.history` joins `payment_record pay ON pay.recharge_id = r.id`. | `EXPLAIN ANALYZE` for customer 1: `Seq Scan on payment_record (rows=52916)` inside a Hash Join, 48 ms alone; `GET /recharges/me?size=10` p50 210 ms / p95 622 ms with 10 users |
| CO-0052 | `LOWER(col) LIKE '%term%'` on 4 columns with OR (no trigram/functional index possible with a leading wildcard), plus a separate `COUNT(*)` with the same predicate; the OR across two joined tables prevents index use. | Seq Scans on `app_user` and `customer_profile`; 19 ms alone, **1.4 s p50 under load** with other traffic |
| CO-0053 | `support_ticket.customer_id` unindexed (indexes cover status/priority and assignee/status only); `ticket_comment.ticket_id` unindexed. | `Seq Scan … Rows Removed by Filter: 5001`, 19.6 ms for one customer's tickets; `GET /support/tickets/me` |
| CO-0054 | `notification_record(event_type)` unindexed; `COUNT` scans 51k rows. | 98 ms |
| CO-0055 | `AdminOperationsService.dashboard` runs ~10 aggregate queries, several full-table `GROUP BY` scans (payments, notifications, tickets, subscriptions, customers) sequentially. | p50 836 ms / p95 2.6 s under load |
| CO-0056 | BCrypt cost 12 on request threads (~250 ms CPU each) + per-request DB validation. | login p50 764 ms with 10 users; unrelated endpoints degrade meanwhile |
| CO-0057 | Lettuce `connect-timeout: 300ms` and no circuit breaker; `CacheErrorHandler` swallows errors but every call retries the connection. | **Verified**: 13 ms cached vs ≈330 ms with Redis stopped |
| CO-0058 | **Lock-order inversion**: `BookingService.cancel` locks `booking_record` (FOR UPDATE OF b) then `payment_record`; `PaymentService.confirm` locks `payment_record` then updates `booking_record`. | **Verified**: 40/40 simultaneous cancel+confirm pairs on one booking produced `deadlock detected` for one of the two (the other committed; end state consistent). PostgreSQL's `deadlock_timeout` (1 s here) makes each attempt slow. Same pattern likely exists for subscriptions (confirm vs cancel) — check. |

## Other observations (not ticketed, fair game)

- `payment_record.idempotency_key` is unique, but nothing prevents two *different* keys creating two payments for the same recharge (CO-0015's mechanism in the data).
- `PaymentService.confirm` trusts the outcome sent by the caller (simulated provider) — by design, a real gateway needs signed webhooks.
- Customer refunds are self-service and immediate (CO-0039 changes that).
- `GET /payments/me`, `/bookings/me`, `/subscriptions/me`, `/support/tickets/me` are unpaged; `/notifications/me` is capped at 200 silently (CO-0040).
- Dead files: `identity/service/TokenService.java` and `telecom/repository/RechargeRepository.java` are 0 bytes; V2/V3 migrations are empty (CO-0013, CO-0009).
- `ConnectOneApplicationTests` shares one huge class and context; individual tests depend on seeded fixtures — a refactor candidate.
- Admin `GET /admin/audit` has no filters and returns an array.
- `SubscriptionExpiryJob`/notification retry are not multi-instance safe (`PaymentExpiryJob` is).
