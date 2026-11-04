# Business workflows

How each flow actually behaves in code (service names in brackets). State names are database values.

## Registration and login [`AuthService`]
`register` creates `app_user(PENDING)` + `customer_profile` and an OTP challenge (code hashed, 5 min, 5 attempts) → `verify-otp`
activates the account and returns tokens. `login`: 5 consecutive failures → `LOCKED` 15 min. Refresh rotates the token; reusing a rotated
token revokes everything. The simulated OTP is returned as `developmentOtp` only while `connectone.otp.expose-code=true`.

## Recharge [`RechargeService`, `PaymentService`]
```
POST /recharges ─► recharge PENDING + payment INITIATED  (one transaction; mobile row locked, must be ACTIVE and owned)
   │   optional couponCode → discount applied to the payment, coupon consumed
   ├─ POST /payments/{id}/confirm SUCCEEDED ─► payment SUCCEEDED, recharge SUCCEEDED (expires_at = now + validity)
   ├─ confirm FAILED ─► payment FAILED, recharge FAILED, coupon released
   └─ nothing for 30 min (and < 7 days old) ─► PaymentExpiryJob: payment EXPIRED, recharge EXPIRED, coupon released
```
Idempotency: the same `idempotencyKey` replays the original recharge; a different mobile/plan with that key → 409.

## Payment callbacks [`PaymentService.confirm`]
Locks the payment row, then: unknown `callbackId` → record it, apply the outcome once; known `callbackId` with the same payment/outcome →
return the current payment (no second effect); known with different payment/outcome → 409. Only `INITIATED` payments accept a new outcome.
Notifications are enqueued with idempotency keys so duplicates are impossible.

## Refunds [`PaymentService.refund`]
Owner calls `POST /payments/{id}/refunds`. Locks the payment; requires `SUCCEEDED`; `SUM(pending+succeeded refunds) + amount ≤ payment.amount`
else 400; inserts the refund (simulated provider ⇒ immediately `SUCCEEDED`). When cumulative refunds equal the amount (**full refund**):
payment → `REFUNDED`; recharge → `REVERSED`; subscription → `CANCELLED` (+event); booking → `REFUNDED` and the seat claim is deleted.
Partial refunds leave everything else untouched. Same `idempotencyKey` returns the original refund.

## Reconciliation [`AdminOperationsService.reconcile`]
Payments the platform could not settle carry `reconciliation_state = RECONCILIATION_REQUIRED | RETRY_REQUIRED`. An admin chooses the provider's
real outcome; the payment becomes `SUCCEEDED`/`FAILED` + `RECONCILED`, the linked recharge/booking is finished (only if still `PENDING/PROCESSING`),
the customer is notified and an audit event with the note is written. Subscription payments are rejected (422).

## Subscriptions [`SubscriptionService`]
`POST /subscriptions` → subscription `PENDING` + payment `INITIATED`; confirm `SUCCEEDED` → `ACTIVE` (`starts_at = now`, `expires_at = now + period`;
a renewal of a live subscription *extends* from the current expiry); confirm `FAILED` → `RENEWAL_FAILED`. `renew` creates a payment (blocked while one is
`INITIATED`). `cancel` allowed from `ACTIVE/PENDING/RENEWAL_FAILED`. `SubscriptionExpiryJob` marks past-due `ACTIVE` as `EXPIRED` and queues reminders
for those expiring within the window. **Entitlement** = an `ACTIVE`, in-window subscription whose plan maps to the content's category.

## Booking [`BookingService`]
`POST /bookings` locks the catalogue row, rejects past/closed schedules, validates the seat number, inserts `booking_record(PENDING)`, **claims the seat**
(`booking_seat_claim` PK ⇒ the loser of a race gets 409), creates the payment, applies any coupon. Confirm `SUCCEEDED` → `CONFIRMED`;
confirm `FAILED` or payment expiry → `CANCELLED`/`EXPIRED` and the seat is released. `cancel`: `PENDING` → cancelled, seat released; `CONFIRMED` → full refund
(payment `REFUNDED`, booking `REFUNDED`, seat released). There is no cancellation-window policy.

## Streaming [`StreamingService`]
`play` checks the customer is `ACTIVE` and has an entitling subscription (above); records `streaming_watch_history`; returns playback metadata. No video is served.

## Support [`SupportService`]
Customer creates (`OPEN`), comments, sees only own tickets and non-internal comments. Staff list/filter, assign (agent must have role `SUPPORT_AGENT`), change status along the
allowed transitions, post internal notes. Every action writes `ticket_history`; `version` is bumped on status changes.

## Coupons [`CheckoutCouponService`, `AdminService`]
Valid when: coupon and offer active, `starts_at < now < ends_at`, `usage_count < usage_limit`, and the customer never redeemed it. Discount = `amount × percent / 100`
(HALF_UP, 2 dp), capped so the payment never falls below ₹1.00. The coupon row is locked `FOR UPDATE` so the limit holds under concurrency (tested: limit 3, 8 racers → 3 winners).
A payment that fails/expires gives the coupon back. Redemption history = `coupon_redemption`.

## Notifications [`NotificationService`]
Events enqueue idempotently (`customer_id + key`). Delivery runs in the retry job: success → `SENT`; failure → `FAILED` with `next_attempt_at = +1 min`, up to 5 attempts.
`POST /admin/notifications/retry` forces a pass.

## Admin audit [`AuditService`]
State-changing admin/business operations write `audit_event` rows tagged with the request's correlation id and log a `connectone.audit` line.
