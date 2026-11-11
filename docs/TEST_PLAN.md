# Manual and exploratory test plan

Use this when you play the **user** and the **tester**. Run against DEV or STAGING-LIKE (docs/LOCAL_ENVIRONMENTS.md) with the large dataset loaded.
For each charter: do it, compare with "Expected", and if it differs write a bug using `.github/ISSUE_TEMPLATE/bug_report.md` (include the correlation id).

## A. Customer journeys (browser)
| # | Charter | Steps | Expected |
|---|---|---|---|
| A1 | Register + OTP | `/register`, fill valid data, use the OTP shown (simulated provider) | lands on dashboard; wrong OTP ×5 locks the challenge |
| A2 | Login errors | wrong password, empty fields, locked account (`cust00500`) | clear message, reference id, no stack trace |
| A3 | Recharge happy path | Recharge → plan → pay | status SUCCEEDED; **notification appears within ~2 s** (via Kafka + notification-service) |
| A4 | Recharge failure | "Simulate failure" | FAILED; coupon (if used) released; failure notification |
| A5 | Coupon | validate a coupon in the dialog, pay | discounted total; same coupon again → rejected |
| A6 | Refund | partial refund then the remainder | cumulative limit enforced; full refund reverses the recharge |
| A7 | Booking | choose seat, pay, cancel | seat freed; refund recorded |
| A8 | Subscription + streaming | subscribe, pay, Play; cancel, Play again | allowed, then 403 message |
| A9 | Support | create ticket, comment | appears in history |
| A10 | Notifications | mark read, unread filter | persists after reload (stored in notification-service) |
| A11 | Heavy account | `cust00001` (445 recharges) | pagination, acceptable speed |
| A12 | Session | wait 15 min / delete token | silent refresh once, else back to login |

## B. Admin / support
| # | Charter | Expected |
|---|---|---|
| B1 | Dashboard | figures load; "events waiting in the outbox" is 0 when Kafka is healthy |
| B2 | Customer 360 | any customer id loads quickly |
| B3 | Reconciliation | resolve a `RECONCILIATION_REQUIRED` payment; audit event with correlation id |
| B4 | Agent permissions | agent cannot open Plans/Offers/Staff pages or call their APIs (403) |
| B5 | Notification deliveries | filter FAILED; retry (admin only) |

## C. Resilience (as a tester)
| # | Charter | Expected |
|---|---|---|
| C1 | Kafka down while paying | payment succeeds; notification delayed, then delivered; outbox counter returns to 0 |
| C2 | notification-service down | payments OK; notifications screen shows an error; no data loss after restart |
| C3 | Redis down | no errors, slight slowdown |
| C4 | Double click "Pay" / replay the same request | exactly one payment effect (idempotency) |
| C5 | Two browsers, same seat | one wins, other gets a clear conflict |

## D. Security checks
Token tampering → 401; customer calling `/api/v1/admin/**` → 403; customer reading another customer's ticket/notification → 404; SQL/script characters in search boxes are harmless; `/actuator/**` (except health) is not reachable through the gateway (404).

## E. Non-functional
`node scripts\loadtest.mjs --users 20` (errors must be 0); compare p95 to docs/DEPLOYMENT_AND_OPERATIONS.md; check `docker stats` memory.

## Bug report quality bar
Title = symptom; steps to reproduce; expected vs actual; correlation id; environment (DEV/STAGING-LIKE, dataset yes/no); severity/impact.
