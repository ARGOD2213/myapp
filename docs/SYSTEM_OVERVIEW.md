# System overview

ConnectOne is a simulated telecom and digital-services platform (think "Airtel Thanks"): one account, one wallet-less
checkout, several services. It exists as an **engineering training environment** — it is built to feel like a product you
have just joined: many interacting modules, years of history in the database, partially imperfect code.

## Who uses it

| Actor | Role code | Uses |
|---|---|---|
| Customer | `CUSTOMER` | recharge numbers, pay, request refunds, subscribe to streaming, book bus/movie/event tickets, watch content, raise support tickets |
| Support agent | `SUPPORT_AGENT` | work the ticket queue, look up customers, monitor payments and refunds (read) |
| Administrator | `ADMIN` | everything above + plans, offers/coupons, catalogue, agents, roles/permissions, reconciliation, audit log, dashboard |

Roles carry **permissions** (`CUSTOMER_READ`, `CUSTOMER_MANAGE`, `PAYMENT_MONITOR`, `PLAN_MANAGE`, `SUPPORT_MANAGE`) stored in the
database and checked with `@PreAuthorize` (role *and* authority).

## Business domains

| Domain | What it does | Main tables |
|---|---|---|
| Identity | registration with OTP, login, refresh tokens, lockout | `app_user`, `otp_challenge`, `refresh_token`, `access_token_revocation`, `app_role`, `app_permission`, `user_role`, `role_permission` |
| Customer | profile, addresses, mobile numbers, activity feed | `customer_profile`, `customer_address`, `mobile_number`, `customer_activity` |
| Recharge | plan catalogue, recharge a mobile number | `recharge_plan`, `recharge_transaction` |
| Payments | one payment per business operation, callbacks, refunds, reconciliation | `payment_record`, `payment_callback`, `refund_record` |
| Subscriptions | streaming plans, renewal, expiry, entitlements | `subscription_plan`, `customer_subscription`, `subscription_event`, `subscription_content_entitlement` |
| Booking | bus/movie/event schedules, seat claims | `booking_catalog_item`, `booking_record`, `booking_seat_claim` |
| Streaming | content catalogue, watch history | `streaming_category`, `streaming_content`, `streaming_watch_history` |
| Support | tickets, comments, status history | `support_ticket`, `ticket_comment`, `ticket_history` |
| Notifications | in-app/SMS/email messages with delivery retry | `notification_record` |
| Promotions | offers, coupons, redemptions applied at checkout | `offer`, `coupon`, `coupon_redemption` |
| Governance | audit trail with correlation ids | `audit_event` |

## How the pieces connect

```
Browser (React)  ──/api──►  Spring Boot API (:18080)  ──JDBC──►  PostgreSQL 17 (:5434, connectone_dev)
   Vite dev :5173                 │  └─ optional ──► Redis (:6380)  (plan catalogue cache, fail-open)
                                  └─ scheduled jobs: payment expiry, subscription expiry/reminders, notification retry
```

A payment is the hub: `payment_record` references **exactly one** of recharge, booking or subscription (a CHECK constraint
enforces it). Confirming a payment moves its business record; refunding it can reverse the recharge, cancel the subscription or
release the booking seat.

## The data you work with

The large dataset (`scripts/seed-dataset.ps1 -Reset`) loads ≈ 10,000 customers, 12,000 mobile numbers, 30,000 recharges,
~53,000 payments, ~3,250 refunds, 12,000 subscriptions, 8,000 bookings, 5,000 tickets, ~51,000 notifications and ~12,000 watch
records, plus controlled investigation scenarios (duplicate charges, reconciliation-required payments, inconsistent states,
missing notifications, stuck tickets, a customer with 445 recharges, a customer firing hundreds of recharges in a day…).
See DATABASE_DESIGN.md → "Dataset scenarios".

Accounts (all share the password you set in `CONNECTONE_SEED_PASSWORD`):
`admin@connectone.test`, `agent01..25@connectone.test`, `cust00001..10000@connectone.test`.

## Where to read next

ARCHITECTURE.md → BUSINESS_WORKFLOWS.md → DATABASE_DESIGN.md → API_REFERENCE.md → ENGINEERING_ONBOARDING.md.
