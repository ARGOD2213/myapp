# DevOps and AWS deployment

This describes the delivery pipeline and the AWS target. **What is verified locally:** both Docker images build, the whole stack runs with
`docker compose up` (Flyway migrates, nginx proxies `/api`, SPA routes work), the Terraform configuration passes `terraform validate`, and the CI steps
are the same commands used locally. **What is not verified:** the GitHub Actions run, `terraform apply`, and the ECS/CloudFront deployment — they need your
GitHub settings and an AWS account, which were not available. Treat the first real run as part of the exercise.

## Pipeline
```
feature branch ──PR──► CI (backend tests on PostgreSQL 17 + Redis, frontend typecheck/test/build, docker build)
                          │ merge to main (squash)
                          ▼
                 CD: build image (tag = commit SHA) → push to ECR → build SPA
                          │
                          ├─► staging  (automatic): new ECS task definition revision → rolling deploy (circuit breaker + auto rollback) → S3 sync + CloudFront invalidation
                          └─► production (manual approval via GitHub Environment) — same image, same SPA bundle
```
Files: `.github/workflows/ci.yml`, `.github/workflows/cd.yml`, `Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `infra/terraform/`.
Authentication to AWS is **OIDC** (the workflow assumes an IAM role; there are no long-lived AWS keys in GitHub).

## AWS architecture (infra/terraform)
| Concern | Service |
|---|---|
| Network | VPC, 2 public subnets (ALB, NAT) + 2 private subnets (app, data) |
| Backend | ECS Fargate service (1 task, 0.5 vCPU / 1 GB) behind an Application Load Balancer; health check `/actuator/health/readiness`; logs to CloudWatch (JSON profile) |
| Database | RDS PostgreSQL 17 (encrypted, 7-day backups, Multi-AZ + deletion protection in prod), private only |
| Cache | ElastiCache Redis (optional cache; the app fails open) |
| Frontend | private S3 bucket + CloudFront (origin access control); CloudFront routes `/api/*` to the ALB, so the browser sees one origin (no CORS) |
| Images | ECR (immutable tags, scan on push, keep last 30) |
| Secrets | Secrets Manager (`db-password`, `jwt-secret`) injected into the task as `CONNECTONE_DB_PASSWORD` / `CONNECTONE_JWT_SECRET` |
| CI/CD access | IAM role trusted by GitHub OIDC for `ARGOD2213/myapp`, limited to ECR push, ECS deploy, S3 sync, CloudFront invalidation |

Keep `desired_count = 1`: `SubscriptionExpiryJob` and the notification retry job are not multi-instance safe (KNOWN_LIMITATIONS.md) — fixing that is a good Level 6 task before scaling out.
The ALB listener is HTTP; TLS terminates at CloudFront. For a custom domain add an ACM certificate + Route 53 records (left as an exercise).
Rough cost if left running: NAT gateway + RDS + ALB + Fargate ≈ a few USD/day — `terraform destroy` when not in use.

## First-time setup (once)
1. **AWS account**: create an admin-capable user for yourself; install AWS CLI v2; `aws configure sso` (or a profile). Pick a region (default `ap-south-1`).
2. **State**: create an S3 bucket (versioned) + DynamoDB lock table and uncomment the `backend "s3"` block in `main.tf`.
3. **Provision staging**:
   ```powershell
   cd infra\terraform
   terraform init
   terraform plan  -var environment=staging -out staging.plan      # READ the plan
   terraform apply staging.plan
   terraform output                                                 # values for GitHub variables below
   ```
   (Use a separate workspace/state for `prod`: `terraform workspace new prod`, `-var environment=prod`.)
4. **GitHub repository settings** (Settings → Secrets and variables → Actions):
   - Secret `AWS_ROLE_ARN` = output `github_deploy_role_arn`.
   - Variables: `AWS_REGION`, `ECR_BACKEND_REPO` (`ecr_backend_repository`), `ECS_CLUSTER`, `ECS_SERVICE_STAGING` / `ECS_SERVICE_PROD`,
     `S3_BUCKET_STAGING` / `S3_BUCKET_PROD`, `CF_DISTRIBUTION_STAGING` / `CF_DISTRIBUTION_PROD`.
   - Environments: create `staging` and `production`; on `production` add yourself as **required reviewer**.
   - Branch protection on `main` (docs/GIT_WORKFLOW.md).
5. **Bootstrap image**: the ECS service starts with an image tag that does not exist yet; push once from your machine or run the CD workflow (`Actions → CD → Run workflow`).
6. **Seed data** (staging only, never production): run the dataset script against an RDS you can reach through a bastion/SSM port-forward — the script is local-container-only by design; adapting it is ticket-sized work.

## Local equivalents
```powershell
docker compose up --build            # postgres + redis + backend + nginx frontend:  http://localhost:8081  (API http://localhost:18081)
docker build -t connectone-backend .   # same image CD pushes
```

## Operating it
- Deploy = merge to `main`. Rollback = Actions → CD → Run workflow on an earlier commit (redeploys its image; migrations are forward-only, see GIT_WORKFLOW.md).
- Observe: CloudWatch Logs `/ecs/connectone-<env>` (search by `correlationId`), RDS Performance Insights (slow SQL), ECS service events, ALB target health.
- Readiness/liveness: `/actuator/health/readiness`, `/actuator/health/liveness`.
- Break-glass: ECS Exec is not enabled by default; add it deliberately and audit its use.

## Hardening backlog (good tickets)
Private API (no public ALB; CloudFront VPC origin), WAF rate limits, KMS-encrypted secrets with rotation, RDS Proxy, alarms (5xx, CPU, connections, reconciliation backlog),
blue/green with CodeDeploy, per-environment Terraform workspaces in CI (`plan` on PR), scanning (Trivy, CodeQL), SBOM.
