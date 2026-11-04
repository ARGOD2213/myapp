# Engineering exercises (how the training backlog works)

The backlog lives in [`tickets/JIRA_BACKLOG.md`](tickets/JIRA_BACKLOG.md) (human-readable) and
[`tickets/jira-import.csv`](tickets/jira-import.csv) (import into Jira/any tracker). **64 tickets** across six levels.

| Level | Theme | Tickets | Typical time | You produce |
|---|---|---|---|---|
| 1 | System familiarisation | CO-0001–0014 | 1–2 h each | notes, diagrams, traced requests |
| 2 | Production support | CO-0015–0026 | 1–3 h | incident notes with evidence (no code change) |
| 3 | Bug fixing | CO-0027–0038 | ½–1 day | failing test → fix → report |
| 4 | Feature development | CO-0039–0050 | 1–3 days | API + DB (+UI) + tests + docs |
| 5 | Performance & reliability | CO-0051–0058 | 1–3 days | measurements before/after, migration, tests |
| 6 | Module ownership | CO-0059–0064 | 3–5 days | design note, cross-layer delivery, incident drill |

Every ticket has: ID, title, priority, difficulty, module, business context, problem statement, expected behaviour, acceptance
criteria, scope. Root causes and solutions are **never** in a ticket; `_ANSWER_KEY.md` (kept separately) is for after your attempt.

## Working rules
1. One branch per ticket, e.g. `CO-0031-refund-validation`. Small commits, imperative messages.
2. **Failing test first** for bugs (PostgreSQL test, see TESTING_GUIDE.md). Run the **entire** suite before review.
3. Schema changes only as a **new** Flyway migration (`V7__…`). Never edit applied migrations.
4. Don't break existing behaviour: if a response shape changes, say so and update API_REFERENCE.md and the frontend.
5. Use the dataset to reproduce; don't invent data that hides the problem.
6. For performance: always record `EXPLAIN (ANALYZE, BUFFERS)` and load-test numbers **before and after**.
7. Update the docs your change makes untrue (docs must match code).

## Engineering report template (submit one per ticket)
```
Ticket / title:
Symptom (what was observed, with correlation id / SQL / screenshot):
Investigation (hypotheses tried, evidence for/against):
Root cause (file:line, why it was wrong, since when if known):
Fix (what changed, why this approach, alternatives rejected):
Tests (new/changed, which fail before the fix):
Risk & regression analysis (what else touches this code? migrations? data repair?):
Rollout/rollback notes:
Follow-ups:
```

## Review checklist (what a reviewer will ask)
- Can you reproduce it with one command or one test? · Is the fix at the right layer? · What about concurrency? · What about existing data?
- Money is `BigDecimal`/`NUMERIC` everywhere? · Are errors using the standard contract? · Are new queries indexed and bounded?
- Is anything logged that shouldn't be (tokens, OTPs, personal data)? · Is there an audit record where an admin changes state?

## Progression
Aim: Level 1–2 in week 1, Level 3 by week 3, Level 4 by week 5, Level 5 by week 7, a Level 6 module by week 8. Ownership means you decide the design,
write the note, deliver across layers, and answer for it in production-style incident drills.
