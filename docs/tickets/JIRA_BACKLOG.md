# ConnectOne engineering backlog

75 tickets in seven levels. Ticket text describes **symptoms and expected behaviour only**: root causes and solutions are intentionally absent
(see `../_ANSWER_KEY.md`, to be opened only after you have submitted your own report). Work in order within a level; Level N assumes Level N-1 skills.
Reproduce against the large dataset (`scripts/seed-dataset.ps1 -Reset`). Submit one engineering report per ticket (template in `../ENGINEERING_EXERCISES.md`).

| ID | Level | Title | Priority | Difficulty | Module |
|---|---|---|---|---|---|
| CO-0001 | 1 | Trace a customer login end to end | P3 | Easy | Identity |
| CO-0002 | 1 | Map every screen of the customer app to its API calls | P3 | Easy | Frontend |
| CO-0003 | 1 | Explain how a payment is tied to what was bought | P3 | Easy | Payments |
| CO-0004 | 1 | Find where each role and permission is enforced | P2 | Easy | Identity |
| CO-0005 | 1 | Trace a recharge from click to rows | P2 | Medium | Recharge |
| CO-0006 | 1 | Trace a payment callback (confirm) and its side effects | P2 | Medium | Payments |
| CO-0007 | 1 | Trace how a seat is claimed and released | P3 | Medium | Booking |
| CO-0008 | 1 | Follow one request through the logs using its correlation id | P3 | Easy | Operations |
| CO-0009 | 1 | Read the Flyway history and explain V2 and V3 | P3 | Easy | Database |
| CO-0010 | 1 | Draw the payment and refund state machine from code and data | P2 | Medium | Payments |
| CO-0011 | 1 | Inventory every scheduled job and what it changes | P3 | Easy | Operations |
| CO-0012 | 1 | Profile the large dataset | P3 | Easy | Data |
| CO-0013 | 1 | Find dead and unused code | P4 | Easy | Codebase |
| CO-0014 | 1 | Explore logs, health and metrics | P3 | Easy | Operations |
| CO-0015 | 2 | Customers report being charged twice for one recharge | P1 | Medium | Payments |
| CO-0016 | 2 | 'I paid but my recharge is pending/failed' | P1 | Medium | Recharge |
| CO-0017 | 2 | Payments stuck in PROCESSING although the provider called back | P1 | Medium | Payments |
| CO-0018 | 2 | Customer 7777 produced hundreds of recharges in one day | P2 | Medium | Recharge |
| CO-0019 | 2 | Support tickets stuck IN_PROGRESS for weeks | P2 | Easy | Support |
| CO-0020 | 2 | Successful payments with no notification | P2 | Medium | Notifications |
| CO-0021 | 2 | Bookings cancelled but the money was captured | P1 | Medium | Booking |
| CO-0022 | 2 | Subscriptions whose state does not match their payments | P2 | Medium | Subscriptions |
| CO-0023 | 2 | Why did this refund return 409 / 400? | P3 | Easy | Payments |
| CO-0024 | 2 | Reconciliation backlog report | P2 | Easy | Payments |
| CO-0025 | 2 | Which accounts are locked and why? | P3 | Easy | Identity |
| CO-0026 | 2 | Reconstruct one customer's month | P3 | Medium | Data |
| CO-0027 | 3 | Same phone number can register twice | P2 | Easy | Identity |
| CO-0028 | 3 | Expired subscription shows as ACTIVE as soon as a renewal is requested | P2 | Medium | Subscriptions |
| CO-0029 | 3 | A coupon redeemed on its own can no longer be used at checkout | P3 | Medium | Promotions |
| CO-0030 | 3 | Booking refund after the event has started | P2 | Medium | Booking |
| CO-0031 | 3 | Ticket queue cannot show how many tickets match | P3 | Easy | Support |
| CO-0032 | 3 | Legacy payment monitor silently truncates results | P3 | Easy | Payments |
| CO-0033 | 3 | Failed notifications are retried at a fixed interval forever-ish | P3 | Medium | Notifications |
| CO-0034 | 3 | Suspended customers can still pay and refund | P2 | Medium | Payments |
| CO-0035 | 3 | How quickly does a disabled account lose access? (verify first) | P1 | Medium | Identity |
| CO-0036 | 3 | Customer search treats wildcard characters literally as wildcards | P3 | Easy | Customer |
| CO-0037 | 3 | Coupon codes differing only by letter case are distinct | P3 | Easy | Promotions |
| CO-0038 | 3 | Offers can be created that have already ended | P4 | Easy | Promotions |
| CO-0039 | 4 | Refund approval workflow | P2 | Hard | Payments |
| CO-0040 | 4 | Server-side pagination for customer lists | P2 | Medium | Platform |
| CO-0041 | 4 | Daily revenue and refund report with CSV export | P3 | Medium | Admin |
| CO-0042 | 4 | Charge auto-renewing subscriptions | P2 | Hard | Subscriptions |
| CO-0043 | 4 | Password reset | P2 | Hard | Identity |
| CO-0044 | 4 | Booking cancellation policy | P3 | Hard | Booking |
| CO-0045 | 4 | Support ticket SLAs | P3 | Medium | Support |
| CO-0046 | 4 | Coupon eligibility rules | P3 | Medium | Promotions |
| CO-0047 | 4 | Customer receipts | P3 | Medium | Payments |
| CO-0048 | 4 | Bulk notification retry from the admin UI | P4 | Easy | Notifications |
| CO-0049 | 4 | Continue watching | P4 | Easy | Streaming |
| CO-0050 | 4 | Notification preferences | P3 | Medium | Notifications |
| CO-0051 | 5 | Recharge history is slow for heavy customers | P2 | Medium | Recharge |
| CO-0052 | 5 | Customer search degrades as customers grow | P2 | Hard | Customer |
| CO-0053 | 5 | Ticket lists slow down as the table grows | P3 | Medium | Support |
| CO-0054 | 5 | Notification analytics query is slow | P4 | Easy | Notifications |
| CO-0055 | 5 | Admin dashboard takes almost a second | P3 | Medium | Admin |
| CO-0056 | 5 | Login throughput under concurrency | P2 | Hard | Identity |
| CO-0057 | 5 | Redis outage adds latency to every request | P2 | Hard | Platform |
| CO-0058 | 5 | Customers sometimes get a server error cancelling a booking as payment confirms | P2 | Hard | Booking |
| CO-0059 | 6 | Own Payments & Reconciliation | P1 | Hard | Payments |
| CO-0060 | 6 | Own Booking | P2 | Hard | Booking |
| CO-0061 | 6 | Own Subscriptions | P2 | Hard | Subscriptions |
| CO-0062 | 6 | Own Notifications and decide the async architecture | P2 | Hard | Notifications |
| CO-0063 | 6 | Incident drill: customers charged twice | P1 | Hard | Payments |
| CO-0064 | 6 | Introduce API v2 for payments without breaking v1 | P3 | Hard | Platform |
| CO-0065 | 7 | Extract the support/ticketing module into its own service | P2 | Hard | Platform |
| CO-0066 | 7 | Rate limiting at the gateway | P2 | Medium | Platform |
| CO-0067 | 7 | Circuit breaker and timeouts between gateway and services | P2 | Medium | Platform |
| CO-0068 | 7 | Distributed tracing across gateway, core and notification-service | P3 | Medium | Observability |
| CO-0069 | 7 | Saga: payment, booking and notification consistency | P1 | Hard | Payments |
| CO-0070 | 7 | Replace the shared JWT secret with asymmetric signing | P1 | Hard | Security |
| CO-0071 | 7 | Flaky Redis cache test in the full suite | P3 | Medium | Testing |
| CO-0072 | 7 | Outbox relay at scale | P3 | Hard | Platform |
| CO-0073 | 7 | Run the platform on a local Kubernetes cluster | P2 | Hard | DevOps |
| CO-0074 | 7 | Update the AWS Terraform and CD pipeline for three services | P2 | Hard | DevOps |
| CO-0075 | 7 | Blue/green deployment rehearsal for core-service | P3 | Medium | DevOps |


## Level 1 — System familiarisation

### CO-0001 — Trace a customer login end to end

**Priority:** P3 · **Difficulty:** Easy · **Module:** Identity · **Level:** 1

**Business context.** You are new on the team and every support question starts with 'can the customer log in?'.

**Problem statement.** You do not yet know which classes, tables and log lines are involved when a customer signs in.

**Expected behaviour.** A written trace of POST /api/v1/auth/login from the browser to the database and back.

**Acceptance criteria**

- Names every class/method touched, in order
- Lists every table read or written and the columns that matter
- Shows the log lines of one login (with correlation id) and explains each
- Explains what happens on the 5th wrong password

**Scope.** Read-only. No code or data changes.

### CO-0002 — Map every screen of the customer app to its API calls

**Priority:** P3 · **Difficulty:** Easy · **Module:** Frontend · **Level:** 1

**Business context.** Support and QA need to know which backend call powers which screen.

**Problem statement.** There is no list of which endpoints each React screen calls.

**Expected behaviour.** A table: screen/route -> HTTP method + path -> request fields -> response fields used.

**Acceptance criteria**

- Covers all 9 customer routes
- Built from the browser Network tab, not guessed
- Notes any screen that calls more than one endpoint or the same endpoint twice

**Scope.** Read-only. Browser devtools + frontend/src/customer.

### CO-0003 — Explain how a payment is tied to what was bought

**Priority:** P3 · **Difficulty:** Easy · **Module:** Payments · **Level:** 1

**Business context.** Finance asks 'what did payment 52913 pay for?' many times a week.

**Problem statement.** The payment table is shared by recharges, bookings and subscriptions and you cannot yet say how.

**Expected behaviour.** A short explanation plus three SQL queries that, given a payment id, return the thing that was bought for each kind.

**Acceptance criteria**

- Explains the rule the database enforces
- Queries run against the large dataset and return rows
- Mentions what happens when someone tries to break the rule

**Scope.** Read-only SQL.

### CO-0004 — Find where each role and permission is enforced

**Priority:** P2 · **Difficulty:** Easy · **Module:** Identity · **Level:** 1

**Business context.** A reviewer asked 'can a support agent see another customer's tickets?' and nobody could answer quickly.

**Problem statement.** Authorization is spread over security configuration, controller annotations and service checks.

**Expected behaviour.** A matrix: endpoint group x role (CUSTOMER / SUPPORT_AGENT / ADMIN) with the exact permission codes required and the file/line that enforces it.

**Acceptance criteria**

- Covers admin, support, payment, customer and public endpoints
- Cites file and line for each rule
- Lists at least two places where the rule is checked in more than one layer

**Scope.** Read-only.

### CO-0005 — Trace a recharge from click to rows

**Priority:** P2 · **Difficulty:** Medium · **Module:** Recharge · **Level:** 1

**Business context.** Recharges are the highest-volume operation and the most common source of tickets.

**Problem statement.** You do not know which rows are created when a customer starts a recharge, in which order, and in which transaction.

**Expected behaviour.** A trace of POST /recharges: the SQL statements, the rows created, the status of each, and what is rolled back if a later step fails.

**Acceptance criteria**

- Lists each INSERT/UPDATE/SELECT FOR UPDATE with the table
- States the initial status of every created row
- Describes the effect of retrying with the same idempotency key
- Includes the correlation-id log lines

**Scope.** Read-only; use the dev database.

### CO-0006 — Trace a payment callback (confirm) and its side effects

**Priority:** P2 · **Difficulty:** Medium · **Module:** Payments · **Level:** 1

**Business context.** Payment confirmation changes several tables; support needs to predict the outcome.

**Problem statement.** It is unclear what changes when a payment is confirmed as SUCCEEDED versus FAILED, and what a repeated callback does.

**Expected behaviour.** A before/after table of every row changed by confirm SUCCEEDED and FAILED for a recharge, a booking and a subscription payment.

**Acceptance criteria**

- Covers all three payment parents
- States what a repeated callback with the same id does
- States what a different outcome with the same callback id does
- Lists the notifications created

**Scope.** Read-only; run the calls yourself and diff the rows.

### CO-0007 — Trace how a seat is claimed and released

**Priority:** P3 · **Difficulty:** Medium · **Module:** Booking · **Level:** 1

**Business context.** 'Seat already taken' complaints need a precise explanation.

**Problem statement.** You cannot yet say which row represents a taken seat or when it disappears.

**Expected behaviour.** A description of the lifecycle of a seat: when it is taken, which events release it, and what the availability endpoint reports at each step.

**Acceptance criteria**

- Identifies the table and key that represent a taken seat
- Covers payment failure, cancellation, expiry and refund
- Demonstrated with real calls against one catalogue item

**Scope.** Read-only plus normal use of the API.

### CO-0008 — Follow one request through the logs using its correlation id

**Priority:** P3 · **Difficulty:** Easy · **Module:** Operations · **Level:** 1

**Business context.** Every production investigation begins with a correlation id.

**Problem statement.** You have not yet used the correlation id to connect a UI error, an API log line and an audit row.

**Expected behaviour.** Pick an admin action in the UI, then find: the response header, the access log line, the audit_event row, and the log line of any business event.

**Acceptance criteria**

- All four artefacts are found by the same id
- Explains where the id is created and where it is cleared
- Notes what is deliberately NOT logged

**Scope.** Read-only.

### CO-0009 — Read the Flyway history and explain V2 and V3

**Priority:** P3 · **Difficulty:** Easy · **Module:** Database · **Level:** 1

**Business context.** New engineers are warned never to edit old migrations.

**Problem statement.** Two migrations are empty files and nobody has explained why that is safe.

**Expected behaviour.** An explanation of flyway_schema_history, the checksum rule, and what would happen if V4 were edited or V2 deleted.

**Acceptance criteria**

- Shows the history table output
- Predicts the exact failure on edit/delete and then proves it on a scratch database (not connectone_dev)
- States the correct way to change a column today

**Scope.** Scratch database only (create a throwaway DB in the ConnectOne container, then drop it).

### CO-0010 — Draw the payment and refund state machine from code and data

**Priority:** P2 · **Difficulty:** Medium · **Module:** Payments · **Level:** 1

**Business context.** Support constantly asks 'what can happen next to this payment?'.

**Problem statement.** The allowed statuses are in the schema but the transitions are scattered through services.

**Expected behaviour.** A diagram of payment, recharge, booking and subscription statuses with the code path that moves each transition.

**Acceptance criteria**

- Every status value in the CHECK constraints appears
- Each arrow cites the method that causes it
- Marks any status that no code path ever produces, and any transition the data shows that no code path produces

**Scope.** Read-only.

### CO-0011 — Inventory every scheduled job and what it changes

**Priority:** P3 · **Difficulty:** Easy · **Module:** Operations · **Level:** 1

**Business context.** Background jobs change data without an API call; on-call must know them.

**Problem statement.** There is no list of scheduled tasks, their frequency or their side effects.

**Expected behaviour.** A table of every @Scheduled method: schedule, tables touched, batch size, what happens if two instances run.

**Acceptance criteria**

- Complete (found by searching the code, not memory)
- States whether each job is safe to run on two instances at once
- Lists the property that changes each schedule

**Scope.** Read-only.

### CO-0012 — Profile the large dataset

**Priority:** P3 · **Difficulty:** Easy · **Module:** Data · **Level:** 1

**Business context.** You will spend weeks working against this data.

**Problem statement.** You do not yet know its size, shape or the controlled anomalies it contains.

**Expected behaviour.** A one-page profile: row counts, status distributions per table, busiest customers, date ranges, and the list of deliberate anomaly scenarios you can find with SQL.

**Acceptance criteria**

- Row counts match scripts/dataset verification output
- Includes at least 8 distinct anomaly queries with counts
- States which numbers change when the dataset is regenerated and which do not

**Scope.** Read-only SQL.

### CO-0013 — Find dead and unused code

**Priority:** P4 · **Difficulty:** Easy · **Module:** Codebase · **Level:** 1

**Business context.** A clean codebase is easier to change.

**Problem statement.** Some files look unused or empty.

**Expected behaviour.** A list of unused files/classes/properties with evidence (no references, zero bytes, unreferenced config) and a recommendation for each.

**Acceptance criteria**

- At least three findings with evidence
- Distinguishes 'safe to delete' from 'looks dead but is referenced indirectly'
- No deletions made (proposal only)

**Scope.** Read-only.

### CO-0014 — Explore logs, health and metrics

**Priority:** P3 · **Difficulty:** Easy · **Module:** Operations · **Level:** 1

**Business context.** On-call needs to know what the running system will tell them.

**Problem statement.** You have not yet seen the health groups, metrics and log formats.

**Expected behaviour.** A cheat-sheet of: health/liveness/readiness URLs and what each reports for admin vs anonymous; five useful metric names; how to switch to JSON logs; how to raise one logger to DEBUG at runtime.

**Acceptance criteria**

- Every item demonstrated on the running system
- Includes the business counters created by payments
- Notes what anonymous callers can and cannot see

**Scope.** Read-only plus runtime logger change on your own instance.


## Level 2 — Production support

### CO-0015 — Customers report being charged twice for one recharge

**Priority:** P1 · **Difficulty:** Medium · **Module:** Payments · **Level:** 2

**Business context.** Support has received several 'charged twice' complaints this week. Each double charge is a refund and a bad review.

**Problem statement.** Some recharges show more than one successful payment row. Nobody knows how many customers or how much money is involved.

**Expected behaviour.** An incident note listing every affected recharge/customer, the money charged twice, the timing pattern, and a safe remediation plan (no data changed).

**Acceptance criteria**

- Complete list with counts and total amount
- Evidence that the extra payments are genuinely duplicates (timestamps, attempt numbers, keys)
- Proposed refund list that cannot over-refund
- Hypotheses for how it could happen, ranked, with what evidence would confirm each

**Scope.** Investigation and recommendation only. Do not change data.

### CO-0016 — 'I paid but my recharge is pending/failed'

**Priority:** P1 · **Difficulty:** Medium · **Module:** Recharge · **Level:** 2

**Business context.** Customers say they were charged but did not get validity.

**Problem statement.** Some recharge rows and their payments disagree about the outcome.

**Expected behaviour.** A report of all recharge/payment disagreements, classified by type, with a recommended action per type.

**Acceptance criteria**

- Every disagreement type found and counted
- For each type: who is out of pocket (customer or company)
- Says which can be auto-fixed and which need a human
- Evidence queries included

**Scope.** Investigation only.

### CO-0017 — Payments stuck in PROCESSING although the provider called back

**Priority:** P1 · **Difficulty:** Medium · **Module:** Payments · **Level:** 2

**Business context.** Ops has a growing pile of payments that never completed although the gateway says success.

**Problem statement.** Some payments are flagged for reconciliation and sit for days.

**Expected behaviour.** Quantify the backlog by age and amount, show the callback evidence for each, and write the runbook an on-call engineer would follow to clear it safely.

**Acceptance criteria**

- Backlog sized by age buckets and money
- Runbook uses the existing admin reconciliation screen/API
- Identifies which recharges are still waiting for these payments
- Lists risks of resolving in bulk

**Scope.** Investigation and runbook. Optionally resolve three payments through the UI to validate the runbook.

### CO-0018 — Customer 7777 produced hundreds of recharges in one day

**Priority:** P2 · **Difficulty:** Medium · **Module:** Recharge · **Level:** 2

**Business context.** Fraud monitoring flagged an unusual volume.

**Problem statement.** One customer has far more recharges in a single day than any other.

**Expected behaviour.** Determine whether it is abuse, a client bug or test traffic, using timing, plans, statuses, payments and numbers used; recommend controls.

**Acceptance criteria**

- Facts established with queries (counts per hour, plan mix, success rate, numbers used)
- A clear conclusion with confidence level and what would change it
- At least two preventive controls proposed (rate limit, velocity rule, alert)

**Scope.** Investigation only.

### CO-0019 — Support tickets stuck IN_PROGRESS for weeks

**Priority:** P2 · **Difficulty:** Easy · **Module:** Support · **Level:** 2

**Business context.** The support manager wants the stale queue cleaned up.

**Problem statement.** Many tickets are IN_PROGRESS with no recent activity; some urgent ones have no owner.

**Expected behaviour.** A report: stale tickets by agent and age, unowned urgent tickets, and tickets whose customer also has a failed payment.

**Acceptance criteria**

- Ages bucketed 14/30/60/90+ days
- Per-agent load table
- Unowned urgent list
- Recommendation for triage order

**Scope.** Investigation only.

### CO-0020 — Successful payments with no notification

**Priority:** P2 · **Difficulty:** Medium · **Module:** Notifications · **Level:** 2

**Business context.** Customers say they never got a receipt.

**Problem statement.** Some successful payments have no corresponding notification record at all, others are stuck unsent.

**Expected behaviour.** Quantify both groups, characterise them (channel, age, event type) and propose how the system should detect and repair each.

**Acceptance criteria**

- Counts for: never created, created-but-FAILED, created-but-PENDING
- Characterisation of each group
- Detection query that could run daily
- Notes any group that is expected/benign

**Scope.** Investigation only.

### CO-0021 — Bookings cancelled but the money was captured

**Priority:** P1 · **Difficulty:** Medium · **Module:** Booking · **Level:** 2

**Business context.** Customers complain their cancelled bookings were not refunded.

**Problem statement.** Some cancelled bookings have a successful payment and no refund.

**Expected behaviour.** Find them, total the amount held, split by age, and explain the possible origins using code and data.

**Acceptance criteria**

- Complete list and total money held
- Distinguishes bookings cancelled by customer, by payment failure and by an operator
- Recommended remediation that cannot double-refund

**Scope.** Investigation only.

### CO-0022 — Subscriptions whose state does not match their payments

**Priority:** P2 · **Difficulty:** Medium · **Module:** Subscriptions · **Level:** 2

**Business context.** Finance found subscribers with access they did not pay for, and payers without access.

**Problem statement.** Some ACTIVE subscriptions have no successful payment; some PENDING ones have.

**Expected behaviour.** List both groups with amounts and dates, and check streaming access implications for each group.

**Acceptance criteria**

- Both groups quantified
- For three sample customers, shows whether play is allowed or denied and why
- Proposes the corrective action for each group

**Scope.** Investigation only; exercise the play endpoint as test customers if needed.

### CO-0023 — Why did this refund return 409 / 400?

**Priority:** P3 · **Difficulty:** Easy · **Module:** Payments · **Level:** 2

**Business context.** A support agent pasted three failed refund calls from a customer and cannot explain the errors.

**Problem statement.** Refund attempts return different errors depending on the payment.

**Expected behaviour.** Reproduce each error class with real payments from the dataset and explain it in plain language, with the exact rule behind each.

**Acceptance criteria**

- At least four distinct error situations reproduced
- Each linked to the rule in code and a SQL check
- Correlation ids included

**Scope.** Investigation only; use dataset payments.

### CO-0024 — Reconciliation backlog report

**Priority:** P2 · **Difficulty:** Easy · **Module:** Payments · **Level:** 2

**Business context.** Finance wants a weekly number.

**Problem statement.** No report exists for payments needing reconciliation.

**Expected behaviour.** A SQL report: count and amount by reconciliation_state, status, age bucket and payment type.

**Acceptance criteria**

- One query or small set of queries, parameter-free
- Verified against the admin dashboard figure
- Runs in under 1 second on the dataset

**Scope.** SQL deliverable only.

### CO-0025 — Which accounts are locked and why?

**Priority:** P3 · **Difficulty:** Easy · **Module:** Identity · **Level:** 2

**Business context.** Customers phone in saying they cannot log in.

**Problem statement.** Some accounts are LOCKED, PENDING or DISABLED; support cannot tell which reason applies.

**Expected behaviour.** A lookup guide: for each account status, what it means, how it arises, how it ends, and a query that explains one customer's login state.

**Acceptance criteria**

- Covers every account status
- Shows the lockout window rule
- Includes a query using app_user and customer_profile
- States what support may and may not do

**Scope.** Read-only.

### CO-0026 — Reconstruct one customer's month

**Priority:** P3 · **Difficulty:** Medium · **Module:** Data · **Level:** 2

**Business context.** A customer disputes their month's charges.

**Problem statement.** There is no single view that lists their recharges, payments, refunds, coupons and notifications chronologically.

**Expected behaviour.** A single SQL query (UNION ALL of events) producing a chronological statement for any customer id and month, plus a worked example for customer 1.

**Acceptance criteria**

- Includes recharges, payments, refunds, coupon redemptions, bookings, subscriptions
- Totals reconcile to payments minus refunds
- Runs fast for the heaviest customer

**Scope.** SQL deliverable only.


## Level 3 — Bug fixing

### CO-0027 — Same phone number can register twice

**Priority:** P2 · **Difficulty:** Easy · **Module:** Identity · **Level:** 3

**Business context.** Support found two accounts for one person; refunds went to the wrong account.

**Problem statement.** Registering with a phone number that differs from an existing one only by a leading '+' is accepted.

**Expected behaviour.** A number identifies exactly one customer regardless of how it was typed.

**Acceptance criteria**

- Registration with +9190... and 9190... of the same number is rejected with a clear 409/400
- Existing validation behaviour for other inputs is unchanged
- Add-mobile-number follows the same rule
- Failing test added first
- Explain what to do about duplicates already in the data

**Scope.** Identity + customer modules. Include data-impact analysis; do not mass-update data.

### CO-0028 — Expired subscription shows as ACTIVE as soon as a renewal is requested

**Priority:** P2 · **Difficulty:** Medium · **Module:** Subscriptions · **Level:** 3

**Business context.** Finance and the customer dashboard count 'active subscribers'; the numbers are inflated and customers are confused.

**Problem statement.** A customer with an expired subscription sees it as ACTIVE the moment they start a renewal, before any payment has succeeded. If they never pay, it stays labelled ACTIVE until a background job notices.

**Expected behaviour.** The subscription's status must reflect reality: it becomes ACTIVE only when the renewal payment succeeds, and an abandoned or failed renewal leaves it expired.

**Acceptance criteria**

- Reproduced with a failing test (status checked right after the renew call, then after confirm SUCCEEDED / FAILED / payment expiry)
- Status and entitlement never disagree at any point of the flow
- Existing renewal of a currently-active subscription is unchanged
- Subscription events describe what actually happened
- Report states how many existing rows could be in an inconsistent state and how you checked

**Scope.** Subscription + payment confirmation code. Do not repair existing data in this ticket.

### CO-0029 — A coupon redeemed on its own can no longer be used at checkout

**Priority:** P3 · **Difficulty:** Medium · **Module:** Promotions · **Level:** 3

**Business context.** Customers say 'I used my coupon, then it was refused when I paid'.

**Problem statement.** There are two ways to redeem a coupon and they interfere.

**Expected behaviour.** Product decision needed and implemented consistently: a coupon is consumed exactly once, by a payment.

**Acceptance criteria**

- Write down the rule you choose and why
- Behaviour is consistent for both entry points
- Concurrency/limit guarantees preserved (existing tests still pass)
- Frontend/API docs updated if an endpoint changes

**Scope.** Admin/coupon + checkout. Backward-compatibility note required.

### CO-0030 — Booking refund after the event has started

**Priority:** P2 · **Difficulty:** Medium · **Module:** Booking · **Level:** 3

**Business context.** Finance found full refunds paid for events that had already happened.

**Problem statement.** A confirmed booking can be cancelled and fully refunded after the schedule's start time.

**Expected behaviour.** Cancellation after the start time must not produce a refund; the customer is told clearly.

**Acceptance criteria**

- Failing test first
- Cancellation after start is rejected or recorded without refund (choose and justify)
- Cancellation before start unchanged
- The 20+ historical cases in the dataset are listed in your report

**Scope.** Booking service. No retroactive data change.

### CO-0031 — Ticket queue cannot show how many tickets match

**Priority:** P3 · **Difficulty:** Easy · **Module:** Support · **Level:** 3

**Business context.** Agents cannot tell if there are 20 or 2,000 open tickets.

**Problem statement.** The staff ticket search returns only the current page; callers cannot know the total or whether there is a next page.

**Expected behaviour.** The response lets a client render accurate pagination.

**Acceptance criteria**

- Total and page information returned
- Backward-compatible or versioned (state which)
- Frontend queue pager updated to use it
- Test with more than one page

**Scope.** Support controller/service + frontend Tickets screen.

### CO-0032 — Legacy payment monitor silently truncates results

**Priority:** P3 · **Difficulty:** Easy · **Module:** Payments · **Level:** 3

**Business context.** An analyst exported 'all failed payments' and got exactly 200 rows.

**Problem statement.** GET /payments returns at most 200 rows without saying so.

**Expected behaviour.** Callers can retrieve complete results or are told the result is limited.

**Acceptance criteria**

- Choose: paging or explicit limit metadata
- Document in API_REFERENCE.md
- No change to authorization
- Test with >200 matching rows

**Scope.** Payment controller/service.

### CO-0033 — Failed notifications are retried at a fixed interval forever-ish

**Priority:** P3 · **Difficulty:** Medium · **Module:** Notifications · **Level:** 3

**Business context.** The SMS vendor throttled us after an outage.

**Problem statement.** Retries of failing notifications are attempted at a constant short interval.

**Expected behaviour.** Retries back off progressively and stop after a bounded number of attempts with a visible final state.

**Acceptance criteria**

- Backoff schedule documented
- Final state after the last attempt is distinguishable from 'will retry'
- Admin deliveries view shows next attempt time
- Unit/integration test of the schedule

**Scope.** Notification service + admin view.

### CO-0034 — Suspended customers can still pay and refund

**Priority:** P2 · **Difficulty:** Medium · **Module:** Payments · **Level:** 3

**Business context.** A suspended account was seen completing a refund.

**Problem statement.** Some payment actions do not take the customer's account status into account while other flows do.

**Expected behaviour.** A consistent rule for what a non-ACTIVE customer can and cannot do, enforced everywhere money moves.

**Acceptance criteria**

- Inventory of every money-moving endpoint and its status check
- Consistent behaviour implemented with tests per endpoint
- Error is the standard 403 contract
- Existing flows for ACTIVE customers unchanged

**Scope.** Payment, recharge, booking, subscription services.

### CO-0035 — How quickly does a disabled account lose access? (verify first)

**Priority:** P1 · **Difficulty:** Medium · **Module:** Identity · **Level:** 3

**Business context.** Security review question after an agent was disabled mid-shift.

**Problem statement.** It is not documented how quickly a disabled or suspended user loses access, for customers and for support agents, or what that costs per request.

**Expected behaviour.** Prove the current behaviour with tests for customers and agents; only change code if you find a real gap, and document the cost of the current approach either way.

**Acceptance criteria**

- Written finding with evidence for customers and for agents
- If a gap exists: fix + test; if none: a regression test that locks the behaviour in
- States the maximum exposure window
- Quantifies the per-request cost of the mechanism that provides it

**Scope.** Identity/security configuration.

### CO-0036 — Customer search treats wildcard characters literally as wildcards

**Priority:** P3 · **Difficulty:** Easy · **Module:** Customer · **Level:** 3

**Business context.** An agent searched for '%' and got every customer.

**Problem statement.** Special characters in the search text change what is matched.

**Expected behaviour.** Search text is matched literally.

**Acceptance criteria**

- Failing test with '%' and '_'
- Normal searches (name, e-mail fragment, phone) unchanged
- Case-insensitivity preserved

**Scope.** Customer repository.

### CO-0037 — Coupon codes differing only by letter case are distinct

**Priority:** P3 · **Difficulty:** Easy · **Module:** Promotions · **Level:** 3

**Business context.** Two coupons 'SAVE10' and 'save10' exist and confuse customers.

**Problem statement.** Coupon code uniqueness and lookup are case sensitive.

**Expected behaviour.** Codes are unique and matched ignoring case.

**Acceptance criteria**

- Creation rejects case-insensitive duplicates
- Lookup at checkout ignores case
- A migration handles any existing conflicts safely (describe)
- Tests for creation and checkout

**Scope.** Admin + coupon service + migration.

### CO-0038 — Offers can be created that have already ended

**Priority:** P4 · **Difficulty:** Easy · **Module:** Promotions · **Level:** 3

**Business context.** Marketing created an offer that was expired on arrival.

**Problem statement.** The admin API accepts offers whose end is in the past.

**Expected behaviour.** Reasonable validation with clear error messages.

**Acceptance criteria**

- Past end date rejected with 400
- Editing/activating rules explained
- Frontend form shows the message
- Tests

**Scope.** Admin DTO/service + frontend Offers form.


## Level 4 — Feature development

### CO-0039 — Refund approval workflow

**Priority:** P2 · **Difficulty:** Hard · **Module:** Payments · **Level:** 4

**Business context.** Finance wants control: customers should request refunds, staff should approve them.

**Problem statement.** Customer refunds are processed instantly with no review.

**Expected behaviour.** A customer submits a refund request; an authorised staff member approves or rejects; money state changes only on approval.

**Acceptance criteria**

- New states and migration, backward compatible with existing refunds
- Customer sees status in the UI
- Staff screen to approve/reject with reason
- Audit events for every decision
- Concurrency-safe (two approvers)
- Docs and tests updated

**Scope.** Payments, admin, frontend, migration.

### CO-0040 — Server-side pagination for customer lists

**Priority:** P2 · **Difficulty:** Medium · **Module:** Platform · **Level:** 4

**Business context.** Heavy customers have hundreds of payments and notifications; pages load slowly.

**Problem statement.** Several customer list endpoints return everything (or a fixed cap) and the UI paginates in the browser.

**Expected behaviour.** Consistent server-side paging with total counts for payments, notifications, bookings and tickets.

**Acceptance criteria**

- Stable response shape documented
- Old clients keep working during a transition (state how)
- Frontend updated
- Load-test comparison for customer 1

**Scope.** Four endpoints + frontend pages.

### CO-0041 — Daily revenue and refund report with CSV export

**Priority:** P3 · **Difficulty:** Medium · **Module:** Admin · **Level:** 4

**Business context.** Finance reconciles daily and copies numbers by hand.

**Problem statement.** No report exists beyond the dashboard totals.

**Expected behaviour.** An admin report by day and payment type (gross, discounts, refunds, net) with CSV download.

**Acceptance criteria**

- Date-range parameters validated
- Numbers tie out to payments and refunds tables (include a test)
- CSV streams without loading everything in memory
- Admin-only

**Scope.** Admin API + frontend page.

### CO-0042 — Charge auto-renewing subscriptions

**Priority:** P2 · **Difficulty:** Hard · **Module:** Subscriptions · **Level:** 4

**Business context.** Customers who ticked auto-renew expect to be charged.

**Problem statement.** autoRenew is stored but nothing renews the subscription.

**Expected behaviour.** A scheduled process initiates renewal payments shortly before expiry through the payment provider, idempotently, with failures visible.

**Acceptance criteria**

- Idempotent per subscription and period
- Failed renewals move to the correct state and notify the customer
- Safe with two application instances
- Audit/metrics for attempts
- Documented schedule properties

**Scope.** Subscription service, provider port, jobs, tests.

### CO-0043 — Password reset

**Priority:** P2 · **Difficulty:** Hard · **Module:** Identity · **Level:** 4

**Business context.** Customers who forget their password have no recovery path.

**Problem statement.** There is no way to reset a password.

**Expected behaviour.** Request a reset via the OTP provider, verify, set a new password; all sessions are revoked.

**Acceptance criteria**

- Does not reveal whether an e-mail exists
- Rate limited and attempt limited
- Old refresh/access tokens revoked
- Strong password rules reused
- No secrets in logs
- UI screens + tests

**Scope.** Identity module + frontend.

### CO-0044 — Booking cancellation policy

**Priority:** P3 · **Difficulty:** Hard · **Module:** Booking · **Level:** 4

**Business context.** The business wants tiered refunds based on time to event.

**Problem statement.** Cancellation refunds are all-or-nothing.

**Expected behaviour.** Configurable tiers (e.g. 100% > 48h, 50% > 24h, 0% afterwards) applied on cancel, with the customer shown the amount before confirming.

**Acceptance criteria**

- Policy stored in the database and editable by admin
- Partial refund recorded correctly (payment stays consistent)
- UI previews the refund
- Edge cases (exactly at a boundary, time zones) tested

**Scope.** Booking, payments, admin, frontend, migration.

### CO-0045 — Support ticket SLAs

**Priority:** P3 · **Difficulty:** Medium · **Module:** Support · **Level:** 4

**Business context.** Managers want to see which tickets are about to breach their response target.

**Problem statement.** Tickets have no due time.

**Expected behaviour.** Each ticket gets a due time from its priority; overdue tickets are flagged and sortable.

**Acceptance criteria**

- Due-time rules documented
- Breach flag in queue and dashboard
- Pausing while WAITING (decide and document)
- Migration for existing tickets
- Tests

**Scope.** Support, admin dashboard, frontend queue.

### CO-0046 — Coupon eligibility rules

**Priority:** P3 · **Difficulty:** Medium · **Module:** Promotions · **Level:** 4

**Business context.** Marketing wants 'minimum order value' and 'only for recharges'.

**Problem statement.** Coupons apply to any payment of any size.

**Expected behaviour.** Optional minimum amount and allowed product types per coupon, enforced at checkout and previewed in validation.

**Acceptance criteria**

- Admin can set rules
- Checkout and /coupons/validate agree
- Clear error messages
- Existing coupons keep working

**Scope.** Promotions, admin UI, migration.

### CO-0047 — Customer receipts

**Priority:** P3 · **Difficulty:** Medium · **Module:** Payments · **Level:** 4

**Business context.** Customers ask for proof of payment.

**Problem statement.** No receipt exists.

**Expected behaviour.** An endpoint and screen returning a receipt (what was bought, amounts, discount, refunds) for a succeeded payment owned by the customer.

**Acceptance criteria**

- Ownership enforced
- Correct for recharge, booking and subscription
- Handles partial refunds
- Printable layout in the UI

**Scope.** Payments API + frontend.

### CO-0048 — Bulk notification retry from the admin UI

**Priority:** P4 · **Difficulty:** Easy · **Module:** Notifications · **Level:** 4

**Business context.** After a vendor outage ops wants to retry only the affected messages.

**Problem statement.** The only retry action is a global 'retry now'.

**Expected behaviour.** Filter failed notifications and retry a selection, with a result summary.

**Acceptance criteria**

- Filter by channel, event type, date
- Selection limit enforced server-side
- Idempotent and audited
- UI feedback for partial failures

**Scope.** Notifications, admin, frontend.

### CO-0049 — Continue watching

**Priority:** P4 · **Difficulty:** Easy · **Module:** Streaming · **Level:** 4

**Business context.** Viewers want to resume where they left off.

**Problem statement.** Progress is stored but never offered back.

**Expected behaviour.** An endpoint and shelf in the UI listing partially watched titles with resume position.

**Acceptance criteria**

- Uses latest progress per title
- Excludes finished titles (define threshold)
- Respects current entitlement
- Tests

**Scope.** Streaming API + frontend.

### CO-0050 — Notification preferences

**Priority:** P3 · **Difficulty:** Medium · **Module:** Notifications · **Level:** 4

**Business context.** Customers want to opt out of SMS marketing but keep payment receipts.

**Problem statement.** All notifications are sent on all channels.

**Expected behaviour.** Per-channel preferences honoured when events are enqueued, except mandatory transactional messages.

**Acceptance criteria**

- Preferences stored and editable
- Mandatory events cannot be disabled (define list)
- Existing idempotency preserved
- UI on the profile page
- Migration with sensible defaults

**Scope.** Notifications, customer, frontend, migration.


## Level 5 — Performance and reliability

### CO-0051 — Recharge history is slow for heavy customers

**Priority:** P2 · **Difficulty:** Medium · **Module:** Recharge · **Level:** 5

**Business context.** Customer 1 (445 recharges) complains the recharge screen lags; support sees it too.

**Problem statement.** GET /recharges/me takes about 200 ms at p50 and 600 ms at p95 with ten concurrent users, far above lighter endpoints.

**Expected behaviour.** Find out why and bring p95 under 100 ms without changing the response.

**Acceptance criteria**

- Evidence of the cause from the database (query plan, not guesses)
- Fix delivered as a Flyway migration and/or code change
- Before/after plan and load-test numbers
- No regression in write latency (state how you checked)

**Scope.** Recharge queries, migration.

### CO-0052 — Customer search degrades as customers grow

**Priority:** P2 · **Difficulty:** Hard · **Module:** Customer · **Level:** 5

**Business context.** The agent console search feels slow and gets slower with data.

**Problem statement.** Searching customers by a name fragment takes over a second under modest load.

**Expected behaviour.** Responsive search at 10k customers and a clear plan for 1M.

**Acceptance criteria**

- Baseline measured (single and concurrent)
- Options compared (e.g. different matching strategies) with trade-offs
- Chosen approach implemented and measured
- Behaviour of existing queries preserved or changes documented

**Scope.** Customer search, migration, possibly frontend debounce.

### CO-0053 — Ticket lists slow down as the table grows

**Priority:** P3 · **Difficulty:** Medium · **Module:** Support · **Level:** 5

**Business context.** The customer 'my tickets' screen and agent queue slow down daily.

**Problem statement.** Per-customer ticket lookups and some queue filters read far more rows than they return.

**Expected behaviour.** Predictable, small, indexed access for the main ticket queries.

**Acceptance criteria**

- Identify every ticket query that scans
- Evidence with EXPLAIN ANALYZE before/after
- Migration reviewed for lock impact on a big table
- No new index without a measured benefit

**Scope.** Support queries, migration.

### CO-0054 — Notification analytics query is slow

**Priority:** P4 · **Difficulty:** Easy · **Module:** Notifications · **Level:** 5

**Business context.** A daily report counting notifications by event type takes about 100 ms and is growing.

**Problem statement.** Counting notifications by event type reads the whole table.

**Expected behaviour.** The report runs in a few milliseconds or is served differently.

**Acceptance criteria**

- Measured baseline
- Solution with trade-offs explained (index vs summary table vs other)
- Write-amplification considered
- Test or script to reproduce the measurement

**Scope.** Notifications, migration.

### CO-0055 — Admin dashboard takes almost a second

**Priority:** P3 · **Difficulty:** Medium · **Module:** Admin · **Level:** 5

**Business context.** Admins open the dashboard many times a day and wait.

**Problem statement.** The dashboard endpoint is the slowest read in the system under load.

**Expected behaviour.** Dashboard in under 200 ms at p95 with correct figures.

**Acceptance criteria**

- Per-statement timing of the endpoint
- Fix without changing the numbers (tests prove equality)
- Considers caching and its staleness trade-off explicitly

**Scope.** Admin operations service.

### CO-0056 — Login throughput under concurrency

**Priority:** P2 · **Difficulty:** Hard · **Module:** Identity · **Level:** 5

**Business context.** A marketing campaign will cause many simultaneous sign-ins.

**Problem statement.** Ten simultaneous logins take about 0.8 seconds at p50; other requests are slower while they run.

**Expected behaviour.** A capacity analysis and safe improvements without weakening password security.

**Acceptance criteria**

- Where the time goes, measured (CPU profile/thread dumps)
- Effect on unrelated endpoints quantified
- Proposed mitigations ranked by risk (what you will NOT do and why)
- At least one implemented with a before/after load test

**Scope.** Identity/security configuration, load test script.

### CO-0057 — Redis outage adds latency to every request

**Priority:** P2 · **Difficulty:** Hard · **Module:** Platform · **Level:** 5

**Business context.** During a Redis outage the catalogue endpoint became noticeably slower though it kept working.

**Problem statement.** While Redis is unreachable each cached call still waits before falling back to the database.

**Expected behaviour.** A cache outage should cost almost nothing after it is detected, and recovery should be automatic.

**Acceptance criteria**

- Reproduce with docker stop/start and measure per-request latency
- Implement fail-fast behaviour with automatic recovery
- Test for outage and recovery
- Document operational behaviour in the playbook

**Scope.** Cache configuration, tests, docs.

### CO-0058 — Customers sometimes get a server error cancelling a booking as payment confirms

**Priority:** P2 · **Difficulty:** Hard · **Module:** Booking · **Level:** 5

**Business context.** Support sees occasional 500s on booking cancellation that vanish when the customer retries.

**Problem statement.** When two operations touch the same booking at the same moment (for example the customer cancels while the payment callback arrives) one of them can fail with a server error.

**Expected behaviour.** Every interleaving of cancel, confirm, expiry and refund on one booking ends in a well-defined, consistent outcome with a meaningful response for the loser.

**Acceptance criteria**

- A race test for each pair of operations that reproduces the failure rate you observe
- Invariants asserted (payment and booking agree, at most one seat claim, no money lost)
- Failures are eliminated or converted into the standard 409 contract
- Report states the lock order each code path uses and the order you standardise on
- Same analysis for subscriptions and recharges

**Scope.** Booking, payments, jobs, tests.


## Level 6 — Independent module ownership

### CO-0059 — Own Payments & Reconciliation

**Priority:** P1 · **Difficulty:** Hard · **Module:** Payments · **Level:** 6

**Business context.** Reconciliation is manual and slow; finance wants it dependable.

**Problem statement.** Stuck payments rely on a person noticing them.

**Expected behaviour.** A design and delivery for automatic detection and resolution of stuck payments with reporting, safe by construction.

**Acceptance criteria**

- Design note with state machine, failure modes and idempotency story
- Automatic resolution for the safe cases; humans for the rest
- Reports and alerts
- Backward-compatible migrations and data repair plan for existing rows
- Regression + concurrency tests
- Runbook

**Scope.** Cross-layer: DB, API, jobs, admin UI, docs.

### CO-0060 — Own Booking

**Priority:** P2 · **Difficulty:** Hard · **Module:** Booking · **Level:** 6

**Business context.** Bookings need holds, policies and reliable release of seats.

**Problem statement.** Seat release, cancellation rules and expiry behaviour are spread across services.

**Expected behaviour.** A coherent booking module: seat holds with expiry, cancellation policy, and a single place for seat lifecycle rules.

**Acceptance criteria**

- Design note including invariants
- Refactor without behaviour regressions (tests first)
- Hold expiry and release verified under concurrency
- UI shows hold countdown

**Scope.** Booking + payments interaction, UI, tests.

### CO-0061 — Own Subscriptions

**Priority:** P2 · **Difficulty:** Hard · **Module:** Subscriptions · **Level:** 6

**Business context.** Retention depends on graceful renewals.

**Problem statement.** Failures end in an abrupt state change with no retry or grace period.

**Expected behaviour.** Renewal with retry schedule (dunning), grace period for entitlement, and clear customer communication.

**Acceptance criteria**

- Design note (states, timers, entitlement during grace)
- Idempotent retries, multi-instance safe
- Notifications at each stage
- Metrics and admin visibility
- Migration for new states

**Scope.** Subscriptions, notifications, streaming entitlement, UI.

### CO-0062 — Own Notifications and decide the async architecture

**Priority:** P2 · **Difficulty:** Hard · **Module:** Notifications · **Level:** 6

**Business context.** More consumers of business events are coming (analytics, fraud).

**Problem statement.** Notifications use a database queue; the team debates introducing a message broker.

**Expected behaviour.** An evidence-based decision and the implementation of the chosen path.

**Acceptance criteria**

- Written architecture decision record comparing at least outbox vs broker with failure modes and operations cost
- Implementation with at-least-once delivery and duplicate protection
- Dead-letter view for operators
- Load test of the chosen approach
- Rollback plan

**Scope.** Notifications and event publishing, ops docs.

### CO-0063 — Incident drill: customers charged twice

**Priority:** P1 · **Difficulty:** Hard · **Module:** Payments · **Level:** 6

**Business context.** You are on call: finance reports a wave of double charges.

**Problem statement.** A realistic incident with data already in the system.

**Expected behaviour.** Handle it end to end like a production incident: triage, mitigate, repair data safely, communicate, and write the postmortem.

**Acceptance criteria**

- Timeline with evidence and correlation ids
- Mitigation that stops new double charges
- Reviewed data-repair script that is idempotent and auditable
- Customer communication draft
- Postmortem with prevention tickets (and at least one regression test added)

**Scope.** Whole system. Dev database only; repair script must be dry-run capable.

### CO-0064 — Introduce API v2 for payments without breaking v1

**Priority:** P3 · **Difficulty:** Hard · **Module:** Platform · **Level:** 6

**Business context.** Clients depend on today's payment responses but we need paging and richer fields.

**Problem statement.** There is no versioning strategy.

**Expected behaviour.** A versioned API with a deprecation plan, shared service logic and a migrated frontend.

**Acceptance criteria**

- Design note (URL vs header versioning, error contract)
- v1 behaviour frozen by tests
- v2 with paging, receipts and refund state
- Frontend moved to v2
- Deprecation/communication plan

**Scope.** Controllers, DTOs, frontend, docs.


## Level 7 — Microservices, platform and DevOps

### CO-0065 — Extract the support/ticketing module into its own service

**Priority:** P2 · **Difficulty:** Hard · **Module:** Platform · **Level:** 7

**Business context.** Support runs on its own cadence and team; it should deploy and scale independently of payments.

**Problem statement.** Support tickets share a database, transactions and code with the rest of core-service. Releasing a ticket change means redeploying payments.

**Expected behaviour.** A support-service with its own database, API behind the gateway, and events for the integration points, delivered with no downtime for existing clients.

**Acceptance criteria**

- Design note: boundary, data ownership, which events cross it (follow docs/MICROSERVICES.md checklist)
- Own database + Flyway; scripted, re-runnable data migration
- Gateway route switch with the old path kept until verified
- Customer-id / agent-id references have no cross-database foreign keys; say how integrity is protected instead
- Contract tests on both sides; compose + CI updated
- Old code and legacy tables removed in a separate, later migration

**Scope.** New module, gateway, compose, CI, docs. Tickets assigned to agents need agent identity: decide how (claims vs lookup).

### CO-0066 — Rate limiting at the gateway

**Priority:** P2 · **Difficulty:** Medium · **Module:** Platform · **Level:** 7

**Business context.** A client bug and a bot both hammered login and recharge endpoints last week.

**Problem statement.** The gateway forwards every request without any limit.

**Expected behaviour.** Per-client (and per-user for authenticated calls) rate limits with correct 429 responses, configurable per route, observable in metrics.

**Acceptance criteria**

- Strict limit on /auth/** and a looser one on the rest
- 429 with Retry-After and the standard JSON error body incl. correlation id
- State store choice justified (in-memory vs Redis) and consequences for several gateway instances
- Test that proves the limit and that normal use is unaffected
- Metric for rejected requests

**Scope.** api-gateway, docs, tests.

### CO-0067 — Circuit breaker and timeouts between gateway and services

**Priority:** P2 · **Difficulty:** Medium · **Module:** Platform · **Level:** 7

**Business context.** When notification-service is down the notifications screen hangs for several seconds before failing.

**Problem statement.** Calls to a dead or slow downstream service are retried by the browser and tie up gateway threads.

**Expected behaviour.** Fail fast with a clear response while a service is unhealthy, recover automatically, and keep unrelated routes unaffected.

**Acceptance criteria**

- Reproduce the slow failure (docker stop) and record the latency before/after
- Circuit breaker + fallback response designed per route (what should the UI see?)
- Thread/connection usage of the gateway measured during the outage
- Retry policy only on idempotent methods; explain why
- Documented in the playbook with the fault-injection steps

**Scope.** api-gateway, frontend error state.

### CO-0068 — Distributed tracing across gateway, core and notification-service

**Priority:** P3 · **Difficulty:** Medium · **Module:** Observability · **Level:** 7

**Business context.** The correlation id helps with logs but nobody can see where time is spent across services.

**Problem statement.** There are no spans; latency in the event pipeline is invisible.

**Expected behaviour.** Traces covering HTTP hops and the Kafka hop, visible in a local trace UI, with the correlation id linked to the trace id.

**Acceptance criteria**

- OpenTelemetry/Micrometer tracing wired in all three services
- Trace context propagated through the Kafka record headers (producer and consumer)
- A local trace backend added to the observability compose profile
- Screenshot-level description of one recharge trace in your report
- Overhead measured

**Scope.** All services, compose, docs.

### CO-0069 — Saga: payment, booking and notification consistency

**Priority:** P1 · **Difficulty:** Hard · **Module:** Payments · **Level:** 7

**Business context.** Once booking and payment live in different services a failure between them leaves money taken without a seat, or a seat without payment.

**Problem statement.** Today a single database transaction hides this problem; after extraction it becomes real.

**Expected behaviour.** A choreographed or orchestrated saga for 'book a seat and pay' with compensations for every failure point.

**Acceptance criteria**

- State machine for the saga incl. every failure and timeout
- Idempotent steps and idempotent compensations
- Failure injection tests (service down between steps, duplicate events, out-of-order events)
- Operator view of stuck sagas
- Decision record: choreography vs orchestration for this flow

**Scope.** Design first; implement across payment/booking modules and events.

### CO-0070 — Replace the shared JWT secret with asymmetric signing

**Priority:** P1 · **Difficulty:** Hard · **Module:** Security · **Level:** 7

**Business context.** Every service holding the signing secret can mint tokens, and a token revoked at logout stays valid on notification-service until it expires.

**Problem statement.** All services share one HS256 secret and the notification service cannot check revocation.

**Expected behaviour.** Only the identity side can sign; other services verify with public keys; revocation behaviour is explicit and tested.

**Acceptance criteria**

- RS256/EdDSA with a JWKS endpoint and key rotation story
- Verification library code shared or duplicated deliberately (justify)
- Maximum exposure window for a revoked token stated and tested; mitigation chosen (short expiry, introspection, deny-list)
- Rollout plan that does not log everyone out
- Update of the local, compose and Terraform configuration

**Scope.** core-service, notification-service, gateway, infra, docs.

### CO-0071 — Flaky Redis cache test in the full suite

**Priority:** P3 · **Difficulty:** Medium · **Module:** Testing · **Level:** 7

**Business context.** A CI run failed once and passed on re-run; the team loses trust in red builds.

**Problem statement.** RedisCacheTests failed once inside the full multi-module run (a thread-pool shutdown error) but passes alone.

**Expected behaviour.** A deterministic test suite: either a root cause fix or a justified isolation.

**Acceptance criteria**

- Reproduce with repeated full runs and record the failure rate
- Root cause explained with evidence (not 'timing')
- Fix verified over at least 20 consecutive full runs
- No test is simply disabled

**Scope.** core-service tests / cache configuration.

### CO-0072 — Outbox relay at scale

**Priority:** P3 · **Difficulty:** Hard · **Module:** Platform · **Level:** 7

**Business context.** The relay publishes one record at a time while holding a database transaction open.

**Problem statement.** Throughput is limited and a slow broker keeps transactions (and row locks) open for seconds.

**Expected behaviour.** A relay design that sustains 1,000 events/s and does not hold locks while waiting for Kafka.

**Acceptance criteria**

- Measure the current maximum events/s and lock hold time
- Alternative designs compared (batching, claim-then-publish, CDC) with failure modes
- Ordering per customer preserved or the change explained
- Chosen design implemented with a load test and outage test
- Backlog/lag metrics and an alert rule

**Scope.** core-service outbox, metrics, docs.

### CO-0073 — Run the platform on a local Kubernetes cluster

**Priority:** P2 · **Difficulty:** Hard · **Module:** DevOps · **Level:** 7

**Business context.** AWS runs this on ECS today, but most employers ask about Kubernetes.

**Problem statement.** There are no Kubernetes manifests.

**Expected behaviour.** kind/minikube manifests (Deployments, Services, ConfigMaps, Secrets, probes, resource limits, an Ingress) that run the whole platform with rolling updates.

**Acceptance criteria**

- Use readiness/liveness probes backed by the existing actuator endpoints
- Resource requests/limits that fit the available memory
- A rolling update and a rollback demonstrated with kubectl
- Kustomize overlays for dev and staging
- A README with every command and what to observe

**Scope.** New k8s/ directory, docs.

### CO-0074 — Update the AWS Terraform and CD pipeline for three services

**Priority:** P2 · **Difficulty:** Hard · **Module:** DevOps · **Level:** 7

**Business context.** The Terraform and CD workflow still describe the single-backend topology that existed before extraction.

**Problem statement.** AWS would only run core-service; the gateway, the notification service, Kafka and its database are missing.

**Expected behaviour.** Infrastructure and pipeline that deploy all services: service-to-service discovery, a managed Kafka, per-service databases, per-service rollouts.

**Acceptance criteria**

- Service discovery approach chosen and justified (Cloud Map / Service Connect / ALB paths)
- Managed Kafka or an explicit alternative with cost estimate
- Second database created reproducibly (not by hand)
- CD builds and deploys each service independently and only when its code changed
- terraform plan is clean; secrets only via Secrets Manager
- Cost estimate and a destroy procedure documented

**Scope.** infra/terraform, .github/workflows, docs/DEVOPS_AWS.md.

### CO-0075 — Blue/green deployment rehearsal for core-service

**Priority:** P3 · **Difficulty:** Medium · **Module:** DevOps · **Level:** 7

**Business context.** The local deploy script rolls in place; production teams often shift traffic between two versions.

**Problem statement.** There is no way to run two versions of core-service side by side and switch traffic.

**Expected behaviour.** A local blue/green procedure with an instant, verified switch and instant rollback.

**Acceptance criteria**

- Two versions running in Compose with different tags
- Traffic switch at the gateway (config or route weights) without downtime
- Database migration compatibility rules written down (expand/contract)
- Smoke test gates the switch; rollback demonstrated

**Scope.** compose, gateway, scripts/deploy-local.ps1, docs.
