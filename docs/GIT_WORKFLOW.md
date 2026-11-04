# Git workflow (how a real team works)

## Model: trunk-based with short-lived branches
`main` is always deployable. All work happens on short-lived branches (hours to 2–3 days) that merge through a pull request.
No direct pushes to `main`. Environments: **staging** deploys automatically from `main`; **production** deploys the same artefact after a manual approval (docs/DEVOPS_AWS.md).

## Branch names
`<ticket>-<short-kebab-description>` — e.g. `CO-0027-normalise-phone-numbers`, `CO-0051-index-payment-recharge-id`.
Other prefixes: `hotfix/<description>` (urgent production fix, branched from `main`), `chore/<description>` (tooling, dependencies, docs).

## Commits
Imperative, ticket first, one logical change each:
```
CO-0027: normalise phone numbers before uniqueness checks

Registration and add-mobile compared raw strings, so +91... and 91...
created two accounts. Normalise to E.164 (leading +) in one place and
add a failing test first.
```
Why a body matters: the reviewer and your future self read `git log` and `git blame`, not your memory.
Avoid "fix", "wip", "update" as whole messages. Don't mix refactoring with behaviour change in one commit.

## Day-to-day commands
```powershell
git switch main; git pull --ff-only                 # start from the latest main
git switch -c CO-0027-normalise-phone-numbers       # one branch per ticket
git add -p                                          # stage deliberately, review hunks
git commit                                          # editor opens: write the message properly
git fetch origin; git rebase origin/main            # keep the branch current (resolve conflicts locally)
git push -u origin HEAD                             # first push of the branch
gh pr create --fill --base main                     # or use the GitHub UI
```
Useful: `git log --oneline --graph --decorate -20`, `git diff main...HEAD` (what the PR contains), `git blame -L 40,60 <file>`,
`git bisect` (find the commit that broke something), `git stash`, `git restore <file>`, `git revert <sha>` (undo a merged change safely).
Never `git push --force` to shared branches; on your own PR branch use `--force-with-lease` after a rebase.

## Pull requests
- Small (under ~400 changed lines where possible), one ticket, template filled in: root cause, tests, risk, rollback.
- CI must pass: backend tests on PostgreSQL, frontend typecheck/tests/build, Docker images build.
- Self-review the diff on GitHub before asking for review; leave comments explaining non-obvious choices.
- Merge: **squash and merge** (one commit per ticket on `main`), delete the branch. The squash message is the ticket line + summary.

## Reviewing (for when you review your own earlier work, or someone else's)
Correctness and edge cases · concurrency and transactions · migrations safe on a large table and backward compatible ·
tests fail before / pass after · errors use the standard contract · nothing sensitive logged · docs updated.

## Releases and versions
Every merge to `main` produces an image tagged with the commit SHA (immutable). Cut a release marker when a meaningful set of tickets ships:
`git tag -a v0.2.0 -m "Refund approval workflow"` then `git push origin v0.2.0`. Rolling back production = re-run the CD workflow for an earlier commit (it redeploys that SHA's image).
Database migrations are forward-only: a rollback that crosses a migration needs a compensating `V#` migration — plan that in the PR.

## Hotfix procedure
1. Branch `hotfix/<what>` from `main`. 2. Minimal fix + regression test. 3. PR, expedited review. 4. Merge → staging deploys → approve production.
5. Write the incident note (what, why, prevention) and create follow-up tickets.

## Protecting `main` (GitHub → Settings → Branches → Add rule for `main`)
Require a pull request before merging; require status checks **Backend**, **Frontend**, **Docker images**; require branches to be up to date;
block force pushes and deletions; (with more than one engineer: require 1 approval and Code Owners review).
