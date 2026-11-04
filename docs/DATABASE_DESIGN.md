# Database design

PostgreSQL 17.11, database `connectone_dev` (dev) / `connectone_test` (tests), schema `public`, **36 tables, 89 indexes, 46 foreign
keys, 44 check constraints** (queried after V6). Schema is owned by Flyway (`src/main/resources/db/migration`); Hibernate only validates.
Money is `NUMERIC(12,2)`; timestamps are `TIMESTAMP` (UTC wall clock — the JVM default zone is forced to UTC).

| Version | Content |
|---|---|
| V1 | core schema (identity, customer, recharge, payment, subscription, booking, support, notification, audit) + 3 recharge plans |
| V2, V3 | **empty files** (historic placeholders already applied; never edit applied migrations) |
| V4 | refresh tokens, OTP challenges, customer activity, payment callbacks, recharge `expires_at`, roles/permissions seed, 2 subscription plans |
| V5 | booking seat claims + price/category, streaming tables, ticket history/category/version, notification idempotency/retry columns, offers/coupons/redemptions, token revocation, demo catalogue |
| V6 | payment states `PROCESSING/CANCELLED/EXPIRED`, `reconciliation_state`, `failure_reason`, `attempt_count`, `discount_amount`, `coupon_id`; recharge states; `coupon_redemption.payment_id/discount_amount`; `audit_event.details`; `mobile_number.is_primary`; `customer_profile.display_language` |

## Tables by domain

**Identity** — `app_user(id, email UQ, password_hash, account_status ACTIVE|LOCKED|DISABLED|PENDING, failed_login_attempts, locked_until)` ·
`app_role`, `app_permission`, `role_permission`, `user_role` · `refresh_token(token_hash UQ, expires_at, revoked_at, rotated_to_hash)` ·
`otp_challenge(challenge_id PK, code_hash, attempt_count, expires_at)` · `access_token_revocation(token_id PK)`.

**Customer** — `customer_profile(id, user_id UQ→app_user, names, phone_number UQ, account_status ACTIVE|SUSPENDED|CLOSED)` ·
`mobile_number(customer_id→, phone_number UQ, status ACTIVE|SUSPENDED|DISCONNECTED, is_primary)` with partial unique index
`uq_mobile_primary_per_customer` (one primary per customer) · `customer_address` · `customer_activity` (feed).

**Recharge** — `recharge_plan(plan_code UQ, price>0, validity_days>0, data_allowance_mb, voice_minutes, active)` ·
`recharge_transaction(customer_id, mobile_number_id, plan_id, amount, status PENDING|PROCESSING|SUCCEEDED|FAILED|CANCELLED|EXPIRED|REVERSED, idempotency_key UQ, expires_at)`.

**Payments** — `payment_record(customer_id, recharge_id | booking_id | subscription_id (exactly one: CHECK), amount, currency, status, idempotency_key UQ, provider_reference UQ, reconciliation_state, failure_reason, attempt_count, coupon_id, discount_amount)` ·
`payment_callback(callback_id PK, payment_id, outcome)` · `refund_record(payment_id, amount>0, status PENDING|SUCCEEDED|FAILED, reason, idempotency_key UQ)`.

Payment status: `INITIATED → (PROCESSING) → SUCCEEDED | FAILED | CANCELLED | EXPIRED`; `SUCCEEDED → REFUND_PENDING → REFUNDED` (full refund).
A **partial** refund leaves the payment `SUCCEEDED`; "partially refunded" is *derived* (`SUM(refund_record.amount) < payment.amount`).
`reconciliation_state`: `NONE | RETRY_REQUIRED | RECONCILIATION_REQUIRED | RECONCILED`.
`payment_record.amount` is the amount **charged** (after discount); `recharge_transaction.amount` is the list price.

**Subscriptions** — `subscription_plan` · `customer_subscription(status PENDING|ACTIVE|EXPIRED|CANCELLED|RENEWAL_FAILED, starts_at, expires_at>starts_at, auto_renew, version)` ·
`subscription_event` · `subscription_content_entitlement(plan_id, category_id)` (which categories a plan unlocks).

**Booking** — `booking_catalog_item(item_type BUS|MOVIE|EVENT, item_code UQ, starts_at, capacity>0, price, available)` ·
`booking_record(booking_code UQ, customer_id, catalog_item_id, seat_or_slot, status, amount, version, idempotency_key)` unique `(customer_id, idempotency_key)` ·
`booking_seat_claim(catalog_item_id, seat_or_slot) PK, booking_id UQ)` — **the seat lock**: a seat is taken iff a claim row exists; claims are deleted on cancel/expire/failed payment.

**Streaming** — `streaming_category`, `streaming_content(content_code UQ, playback_url, active)`, `streaming_watch_history(customer_id, content_id, progress_seconds)`.

**Support** — `support_ticket(ticket_code UQ, customer_id, assigned_user_id, priority, status OPEN|IN_PROGRESS|WAITING|RESOLVED|CLOSED, category, version)` ·
`ticket_comment(is_internal)` · `ticket_history(event_type, details)`.

**Notifications** — `notification_record(customer_id, channel IN_APP|EMAIL|SMS, event_type, status PENDING|SENT|FAILED, attempt_count, next_attempt_at, last_error, read_at, idempotency_key)`; unique `(customer_id, idempotency_key)` makes enqueue idempotent.

**Promotions** — `offer(discount_percent, starts_at<ends_at, active)` · `coupon(coupon_code UQ, usage_limit, usage_count ≤ limit)` · `coupon_redemption(coupon_id, customer_id) UQ, payment_id, discount_amount)`.

**Audit** — `audit_event(actor_user_id, event_type, entity_type, entity_id, correlation_id, details)`; the `correlation_id` equals the HTTP `X-Correlation-Id`, so an audit row can be matched to the API log lines of the same request.

## Deletion behaviour

Customer-owned business rows use `ON DELETE RESTRICT` (a customer with payments cannot be deleted — tested). Pure children use `CASCADE`
(`refresh_token`, `otp_challenge`, `ticket_comment`, `booking_seat_claim`…). Audit `actor_user_id` uses `SET NULL`.

## Indexes you should know

`idx_payment_customer_created(customer_id, created_at)` · `idx_payment_status_created` · `idx_payment_reconciliation` (partial) ·
`idx_recharge_customer_created` · `idx_subscription_expiry(status, expires_at)` · `idx_booking_schedule` · `idx_notification_delivery(status, next_attempt_at)` ·
`idx_ticket_status_priority` · `idx_ticket_assignee_status`. Not every foreign key is indexed — finding which, and proving it with
`EXPLAIN (ANALYZE)`, is part of the training (Level 5 tickets).

## Important queries (read these in code, then run `EXPLAIN` on them)

- Recharge history: `RechargeService.history` (joins `recharge_transaction`, `recharge_plan`, `payment_record`).
- Refund eligibility: `PaymentService.refund` (`SUM(refund_record.amount) … FOR UPDATE` on the payment).
- Seat availability: `BookingService.availability` (capacity minus `booking_seat_claim`).
- Entitlement: `StreamingService.play` (active, in-window subscription → plan → entitlement → category).
- Admin payment monitor: `AdminOperationsService.payments`.

## Dataset scenarios (large dataset, deterministic)

Generated by `scripts/dataset/large_dataset.sql` — all keyed by modular arithmetic on ids so you can find them with SQL:

| Scenario | How to find it |
|---|---|
| Duplicate successful charges for one recharge | `SELECT recharge_id FROM payment_record WHERE status='SUCCEEDED' AND recharge_id IS NOT NULL GROUP BY 1 HAVING count(*)>1` |
| Provider said SUCCEEDED but payment stuck `PROCESSING` | `reconciliation_state='RECONCILIATION_REQUIRED'` joined to `payment_callback` |
| Failed payments flagged for retry | `reconciliation_state='RETRY_REQUIRED'` |
| Recharge/payment status disagree | `recharge_transaction.status` vs `payment_record.status` |
| Subscription state vs payment state disagree | `customer_subscription.status='PENDING'` with a `SUCCEEDED` payment, `ACTIVE` with failed first payment |
| Payments with no notification | no `notification_record` row with key `payment-<id>-…` |
| Notifications stuck `PENDING`/`FAILED` | `notification_record.status` |
| Heavy and idle customers | customer 1 (≈445 recharges) vs ≈1,960 customers with none; customer 7777 has hundreds of recharges in one day |
| Cancelled bookings with captured money | `booking_record.status='CANCELLED'` + payment `SUCCEEDED` and no refund |
| Bookings cancelled after the event started | cancelled with `updated_at` > `booking_catalog_item.starts_at` |
| Tickets stuck `IN_PROGRESS`, unowned urgent tickets | `support_ticket` |

`scripts/dataset/verify_dataset.sql` asserts the invariants (refunds ≤ payment, mobile belongs to customer, coupons ≤ limit…) and counts these scenarios.
