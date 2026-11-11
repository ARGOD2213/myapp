# ConnectOne

A simulated telecom and digital-services platform (recharge, payments/refunds, streaming subscriptions, bookings, support,
notifications, admin) built as a **realistic engineering training product**: services behind an API gateway (strangler-fig migration from a
monolith, Kafka + outbox between services), a large synthetic dataset, a React frontend, PostgreSQL integration tests, Docker/Compose
environments, CI/CD and AWS infrastructure code, and a 75-ticket backlog.

```
Browser -> api-gateway :18080 -+-> core-service :18081 ------> PostgreSQL (connectone_dev) --outbox--> Kafka --+
                               +-> notification-service :18082 <-- consumes events <-----------------------+
                                        |
                                        +-> PostgreSQL (connectone_notifications)        (Redis: optional cache)
```
| Part | Tech |
|---|---|
| Services | `services/core-service` (Boot 4.1), `services/notification-service` (Boot 4.1), `services/api-gateway` (Spring Cloud Gateway, Boot 4.0) — see [docs/MICROSERVICES.md](docs/MICROSERVICES.md) |
| Backend stack | Java 25, Spring MVC/Security (JWT), JdbcTemplate, Flyway, HikariCP, Actuator + Micrometer/Prometheus |
| Messaging / data | Kafka (KRaft) with transactional outbox, PostgreSQL 17 database-per-service, optional Redis cache |
| Frontend | React 18, TypeScript, Vite (`frontend/`) |
| Tests | JUnit 5 on **real PostgreSQL** and **embedded Kafka**, vitest, Playwright-core browser journeys, load and event-pipeline smoke tests |
| Delivery | multi-stage Dockerfile, Docker Compose (staging-like + observability profile), GitHub Actions CI/CD, Terraform for AWS |

## Quick start (Windows) — details in [docs/LOCAL_ENVIRONMENTS.md](docs/LOCAL_ENVIRONMENTS.md) and [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)
```powershell
docker start connectone-postgres                            # first-time creation: LOCAL_SETUP section 1
.\scripts\start-all.ps1                                      # starts Kafka+Redis containers, then gateway + core + notification (JVMs on the host)
.\scripts\seed-dataset.ps1 -Reset                           # optional: 10,000 customers (stop the services first; needs CONNECTONE_SEED_PASSWORD)
cd frontend; npm install; npm run dev                       # http://localhost:5173
.\scripts	est.ps1                                          # all backend tests (PostgreSQL + embedded Kafka)
node scripts\smoke-events.mjs                               # proves recharge -> Kafka -> notification end to end
docker compose up -d --build                                # staging-like deployment in containers: http://localhost:8081
```
Secrets come from Windows user environment variables (`.env.example` lists names only). Nothing secret is stored in the repo.

## Documentation (read in this order)
[SYSTEM_OVERVIEW](docs/SYSTEM_OVERVIEW.md) → [ARCHITECTURE](docs/ARCHITECTURE.md) → [BUSINESS_WORKFLOWS](docs/BUSINESS_WORKFLOWS.md) →
[DATABASE_DESIGN](docs/DATABASE_DESIGN.md) → [API_REFERENCE](docs/API_REFERENCE.md) → [ENGINEERING_ONBOARDING](docs/ENGINEERING_ONBOARDING.md).
Also: [TAKEOVER_AUDIT](docs/TAKEOVER_AUDIT.md), [TESTING_GUIDE](docs/TESTING_GUIDE.md), [DEBUGGING_PLAYBOOK](docs/DEBUGGING_PLAYBOOK.md),
[DEPLOYMENT_AND_OPERATIONS](docs/DEPLOYMENT_AND_OPERATIONS.md), [KNOWN_LIMITATIONS](docs/KNOWN_LIMITATIONS.md),
[ENGINEERING_EXERCISES](docs/ENGINEERING_EXERCISES.md), [backlog](docs/tickets/JIRA_BACKLOG.md),
[MICROSERVICES](docs/MICROSERVICES.md), [LOCAL_ENVIRONMENTS](docs/LOCAL_ENVIRONMENTS.md), [TEST_PLAN](docs/TEST_PLAN.md),
[GIT_WORKFLOW](docs/GIT_WORKFLOW.md), [DEVOPS_AWS](docs/DEVOPS_AWS.md), [INTERVIEW_PREP_ROADMAP](docs/INTERVIEW_PREP_ROADMAP.md), [CONTRIBUTING](CONTRIBUTING.md).
`docs/_ANSWER_KEY.md` contains root causes — open it only after attempting the tickets.

## How to work on it
Tickets are GitHub Issues (`CO-XXXX`, created from `docs/tickets/` by `scripts/create-github-issues.ps1`). Branch per ticket, failing test first, pull request, green CI,
squash-merge - see [CONTRIBUTING](CONTRIBUTING.md) and [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md). `main` deploys to staging automatically and to production after
manual approval on AWS (ECS Fargate, RDS PostgreSQL, ElastiCache, S3 + CloudFront; Terraform in `infra/terraform`): [docs/DEVOPS_AWS.md](docs/DEVOPS_AWS.md).
`docker compose up --build` runs the full stack locally.

## Safety
ConnectOne uses only its own containers. Never touch `ai-lab-postgres` (5433). All data is synthetic.
