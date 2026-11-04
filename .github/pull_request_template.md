## Ticket
Closes #<issue number> (CO-XXXX)

## What and why
<!-- Symptom or goal, in two or three sentences. -->

## Root cause / approach
<!-- Bugs: file:line and why it was wrong. Features: design in brief and alternatives rejected. -->

## Tests
- [ ] A test that **fails before** the change and passes after (PostgreSQL test for backend)
- [ ] `scripts\test.ps1` (all backend tests) green
- [ ] `npm run typecheck && npm test` green (if the frontend changed)

## Risk and rollout
- [ ] Schema change is a **new** Flyway migration (`V7__...`); no applied migration edited
- [ ] API/response backward compatible, or the break is called out and docs/frontend updated
- [ ] Nothing sensitive logged; money uses `BigDecimal` / `NUMERIC`
- [ ] Docs updated where they became untrue (API_REFERENCE, DATABASE_DESIGN, ...)

## Rollback
<!-- Revert commit? compensating migration? data repair? -->
