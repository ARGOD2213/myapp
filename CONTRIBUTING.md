# Contributing to ConnectOne

This repository is run like a product team's repository, even with one engineer: every change is a ticket, a branch, a pull request,
a green pipeline and a merge. The full conventions are in [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md); the short version:

1. Pick a ticket (GitHub Issue, `CO-XXXX`; backlog in `docs/tickets/JIRA_BACKLOG.md`). Assign yourself.
2. `git switch main && git pull && git switch -c CO-0027-normalise-phone-numbers`
3. Write a failing test, fix, run `scripts\test.ps1` (backend) and `npm test` (frontend).
4. Small commits in the imperative mood: `CO-0027: normalise phone numbers before uniqueness checks`.
5. `git push -u origin <branch>` and open a PR using the template. CI must be green.
6. Review your own diff first, then merge with **squash** and delete the branch. `main` is always releasable.

Never commit secrets, `.env` files, database dumps, or real personal data. Schema changes are new Flyway migrations only.
