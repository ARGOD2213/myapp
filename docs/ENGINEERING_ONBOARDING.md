# Engineering onboarding — first week

Goal of week 1: be able to **trace any request end to end without help** (UI → controller → service → SQL → rows → log line)
and know where every domain lives. No code changes are expected before Day 4. Keep a notebook: each day ends with
"what I can now explain" written in your own words — that is what you will be asked in review.

Rules of the programme: do not ask an AI to solve tickets; use the docs, the code, `EXPLAIN`, logs and tests. Read
`docs/_ANSWER_KEY.md` **only after** you have submitted your own report for the related ticket.

## Day 1 — Run it, see it
- Read SYSTEM_OVERVIEW.md, LOCAL_SETUP.md. Start Postgres/Redis containers, run `scripts\start-backend.ps1`, load the dataset, run `npm run dev`.
- Click through both apps (customer: `cust00001`, admin: `admin@`). Perform one recharge, one booking, one ticket.
- Run the test suite once (`scripts\test.ps1`) and the browser journey.
- **Deliverable:** a one-page list of every screen and the API call(s) behind it (use the browser Network tab).
- Tickets: CO-0001 … CO-0004.

## Day 2 — Follow a request
- Read ARCHITECTURE.md. Open `CorrelationIdFilter`, `SecurityConfiguration`, `JwtAuthenticationFilter`, `ApiExceptionHandler`.
- Trace `POST /api/v1/recharges`: controller → `RechargeService.initiate` → SQL statements → rows created → log lines (correlation id).
- Do it for `POST /payments/{id}/confirm` and `POST /bookings`.
- **Deliverable:** a sequence diagram (hand-drawn is fine) for recharge + payment confirmation, naming each table touched.
- Tickets: CO-0005 … CO-0009.

## Day 3 — Read the database
- Read DATABASE_DESIGN.md. In psql: list tables (`\dt`), describe `payment_record` (`\d payment_record`), find one customer's full story with joins.
- Run the dataset scenario queries. Use `EXPLAIN` on the recharge-history query once, just to read a plan (analysis comes in Level 5).
- **Deliverable:** an ER sketch of payment ↔ recharge/booking/subscription ↔ refund ↔ notification, with the constraint that ties them.
- Tickets: CO-0010 … CO-0014.

## Day 4 — Production support
- Read DEBUGGING_PLAYBOOK.md §1, 11, 12, 13.
- Handle two "support requests" as an on-call engineer (CO-0015 … CO-0020): find the customer, reconstruct the timeline from payments, callbacks, notifications and audit, write the finding. **Do not change code or data.**
- **Deliverable:** two short incident notes: what happened, evidence (queries/log ids), proposed fix, who must be told.

## Day 5 — First change
- Read TESTING_GUIDE.md. Pick one Level-3 ticket (CO-0027 … CO-0038), write a failing test first, fix, run the full suite, write the engineering report (template in ENGINEERING_EXERCISES.md).
- **Deliverable:** one reviewed PR-sized change with test + report.

## Weeks 2–8 (outline)
Levels 3 → 6 in ENGINEERING_EXERCISES.md: bug fixing, features, performance/reliability, then own a module end to end (design note, implementation across API/DB/UI, regression tests, a simulated incident).

## Review questions you should be able to answer by Friday
1. What happens, row by row, when a recharge payment succeeds? When it fails? When nobody confirms it?
2. Why can two people not book the same seat? Which constraint enforces it?
3. How does the system stop a duplicate payment callback from crediting twice?
4. Where do permissions come from and where are they enforced?
5. What is a correlation id and where will you see it?
6. Which tables would you query to explain "I was charged but my recharge didn't happen"?
7. Why must `V1`–`V5` never be edited, and how would you change a column today?
