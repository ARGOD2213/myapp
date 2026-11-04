# ConnectOne

A simulated telecom and digital-services platform (recharge, payments/refunds, streaming subscriptions, bookings, support,
notifications, admin) built as a **realistic engineering training product**: a modular monolith with a large synthetic dataset,
a React frontend, PostgreSQL integration tests, operational tooling and a 64-ticket backlog.

| Part | Tech |
|---|---|
| Backend | Java 25, Spring Boot 4.1.2, Spring MVC/Security (JWT), JdbcTemplate (+1 JPA entity), Flyway, HikariCP, Actuator |
| Data | PostgreSQL 17 (dedicated container, port 5434), optional Redis 7 cache (port 6380) |
| Frontend | React 18, TypeScript, Vite (`frontend/`) |
| Tests | JUnit 5 on **real PostgreSQL**, vitest, Playwright-core browser journeys |

## Quick start (Windows)
Full instructions: [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md). In short:
```powershell
docker start connectone-postgres connectone-redis          # see LOCAL_SETUP §1 for first-time creation
.\scripts\seed-dataset.ps1 -Reset                         # 10,000 customers (needs CONNECTONE_SEED_PASSWORD)
.\scripts\start-backend.ps1                               # http://localhost:18080  (health: /actuator/health)
cd frontend; npm install; npm run dev                     # http://localhost:5173
.\scripts\test.ps1                                        # 27 backend tests on connectone_test
```
Secrets come from Windows user environment variables (`.env.example` lists names only). Nothing secret is stored in the repo.

## Documentation (read in this order)
[SYSTEM_OVERVIEW](docs/SYSTEM_OVERVIEW.md) → [ARCHITECTURE](docs/ARCHITECTURE.md) → [BUSINESS_WORKFLOWS](docs/BUSINESS_WORKFLOWS.md) →
[DATABASE_DESIGN](docs/DATABASE_DESIGN.md) → [API_REFERENCE](docs/API_REFERENCE.md) → [ENGINEERING_ONBOARDING](docs/ENGINEERING_ONBOARDING.md).
Also: [TAKEOVER_AUDIT](docs/TAKEOVER_AUDIT.md), [TESTING_GUIDE](docs/TESTING_GUIDE.md), [DEBUGGING_PLAYBOOK](docs/DEBUGGING_PLAYBOOK.md),
[DEPLOYMENT_AND_OPERATIONS](docs/DEPLOYMENT_AND_OPERATIONS.md), [KNOWN_LIMITATIONS](docs/KNOWN_LIMITATIONS.md),
[ENGINEERING_EXERCISES](docs/ENGINEERING_EXERCISES.md), [backlog](docs/tickets/JIRA_BACKLOG.md),
[GIT_WORKFLOW](docs/GIT_WORKFLOW.md), [DEVOPS_AWS](docs/DEVOPS_AWS.md), [CONTRIBUTING](CONTRIBUTING.md).
`docs/_ANSWER_KEY.md` contains root causes — open it only after attempting the tickets.

## How to work on it
Tickets are GitHub Issues (`CO-XXXX`, created from `docs/tickets/` by `scripts/create-github-issues.ps1`). Branch per ticket, failing test first, pull request, green CI,
squash-merge - see [CONTRIBUTING](CONTRIBUTING.md) and [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md). `main` deploys to staging automatically and to production after
manual approval on AWS (ECS Fargate, RDS PostgreSQL, ElastiCache, S3 + CloudFront; Terraform in `infra/terraform`): [docs/DEVOPS_AWS.md](docs/DEVOPS_AWS.md).
`docker compose up --build` runs the full stack locally.

## Safety
ConnectOne uses only its own containers. Never touch `ai-lab-postgres` (5433). All data is synthetic.
