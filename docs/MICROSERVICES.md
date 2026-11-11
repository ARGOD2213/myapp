# Microservices architecture

ConnectOne started as a modular monolith. It is being decomposed with the **strangler-fig** approach, the way real teams do it:
extract one bounded context at a time behind a stable API, verify, then take the next. Nothing here is a big-bang rewrite, and the
remaining monolith ("core-service") is deliberately left as the next set of tickets (see "What is NOT extracted yet").

```
Browser ──► (nginx / Vite)  ──►  api-gateway :18080 ──┬──► core-service :18081 ──► PostgreSQL  connectone_dev
                                  (routing, correlation id)       │   outbox_event ──(relay)──┐
                                                       └──► notification-service :18082        ▼
                                                              ▲   │                         Kafka  connectone.events.v1 (3 partitions)
                                                              │   └──► PostgreSQL connectone_notifications        │
                                                              └──────────── consumer group "notification-service" ◄┘  (+ .DLT dead-letter topic)
```

| Service | Boot | Owns | Talks to |
|---|---|---|---|
| **api-gateway** `:18080` | 4.0.9 + Spring Cloud 2025.1.4 | routing, edge correlation id | HTTP → core, notification |
| **core-service** `:18081` | 4.1.2 | identity, customers, recharge, payments, subscriptions, booking, streaming, support, admin; DB `connectone_dev` | PostgreSQL, Redis (cache), Kafka (producer via outbox) |
| **notification-service** `:18082` | 4.1.2 | notification storage + delivery + its REST API; DB `connectone_notifications` | Kafka (consumer), PostgreSQL |

## Concepts → where to see them (interview map)

| Concept | Where it lives | How to demonstrate it |
|---|---|---|
| **Bounded context / service boundary** | notification domain removed from core; only events cross the boundary | `NotificationService.enqueue` in core now just writes an event |
| **Database per service** | `connectone_dev` (core) vs `connectone_notifications`; no cross-database queries or foreign keys (`customer_id` is a plain reference) | `scripts/migrate-notifications.ps1` — the data had to be *copied*, not joined |
| **API gateway** | `services/api-gateway` (Spring Cloud Gateway MVC): path routing, correlation id, timeouts | `curl localhost:18080/api/v1/notifications/me` is routed to a different service than `/payments/me` |
| **Service discovery** | static URLs from env (`CORE_SERVICE_URL`); platform DNS in Compose/Kubernetes | `docker-compose.yml` (`http://core-service:18081`). Eureka/Consul only pays off with dynamic fleets |
| **Externalised configuration** | env vars + `application.yml`; secrets via env/Secrets Manager | `.env.example`, Terraform `secrets` |
| **Async messaging with Kafka** | topic `connectone.events.v1`, key = customerId (per-customer ordering), 3 partitions | `docker exec connectone-kafka /opt/kafka/bin/kafka-topics.sh --describe ...` |
| **Transactional outbox** | `OutboxService` + `OutboxRelay` (core), table `outbox_event` (V7) | `OutboxTests`; stop Kafka and keep using the app (drill below) |
| **At-least-once delivery + idempotent consumer** | relay may resend; consumer unique index `(customer_id, idempotency_key)` + `ON CONFLICT DO NOTHING` | `NotificationServiceTests.concurrentDuplicateDeliveries…`, `KafkaRoundTripTests` |
| **Dead-letter topic & retry/back-off** | `KafkaConsumerConfiguration`: 3 retries (1s,2s,4s) then `connectone.events.v1.DLT` | `KafkaRoundTripTests` (poison message parked) |
| **Event contract & schema evolution** | `docs/events/notification-requested.v1.json`; consumer ignores unknown fields; `schemaVersion` | `parsesTheExactEventCorePublishesAndToleratesNewFields` |
| **Distributed tracing (poor man's)** | one `X-Correlation-Id` from gateway → core → Kafka header → notification logs | `grep <id> logs\*.log` |
| **Resilience / graceful degradation** | Kafka down ⇒ core keeps working, events wait; Redis down ⇒ fail-open | drill below; `RedisCacheTests` |
| **AuthN across services** | HS256 JWT issued by core, verified by notification (shared secret) | `JwtVerifier` + trade-off below |
| **Observability** | Actuator health/liveness/readiness, Micrometer → Prometheus → Grafana, structured logs | `docker compose --profile observability up -d` → Grafana :3000 |
| **Containerisation & orchestration** | one multi-stage `Dockerfile` (non-root, healthchecks), `docker-compose.yml` with `depends_on: service_healthy` | `docker compose up -d --build` |
| **CI/CD, environments** | `.github/workflows`, `scripts/deploy-local.ps1` (blue/green + rollback rehearsal) | docs/LOCAL_ENVIRONMENTS.md |
| **Polyglot versions per service** | gateway runs Boot 4.0.9 because Spring Cloud for 4.1 is only an RC | `services/api-gateway/pom.xml` |

## Drill you should be able to explain: "Kafka is down"
1. `docker stop connectone-kafka`. 2. Recharge + pay via the UI: **succeeds** (0.6 s measured). 3. Dashboard "events waiting in the outbox" climbs (measured: 2).
4. `docker start connectone-kafka`. 5. Within ~15–20 s the notifications appear (measured: 17 s) and the backlog returns to 0.
Why it works: the event row was committed in the *same transaction* as the payment; the relay publishes later; nothing is lost and nothing is sent for a rolled-back payment.
What can still go wrong: the relay sends, Kafka acknowledges, the process dies before marking the row published → **duplicate** → absorbed by the idempotent consumer.

## Trade-offs made on purpose (and what to say in an interview)
- **Shared HS256 secret.** notification-service verifies signature+expiry only; a token revoked at logout still works there until it expires (≤15 min), whereas core also checks its revocation table. Production: asymmetric keys (RS256/JWKS) and short-lived tokens, or token introspection → ticket CO-0070.
- **Relay holds a DB transaction while waiting for Kafka (≤5 s per record).** Fine at this volume; at scale use batching, `SKIP LOCKED` worker pools or CDC (Debezium) → ticket CO-0072.
- **Static routing instead of Eureka.** Simplicity; the platform provides DNS.
- **One PostgreSQL instance, two databases, one role locally.** Isolation by database, not by instance or credentials; production uses separate instances/roles (Terraform: one RDS per service).
- **Gateway has no rate limiting / circuit breaker / auth yet.** Listed as tickets (CO-0066, CO-0067); each service still enforces auth itself ("defence in depth").
- **No distributed transaction.** Cross-service consistency is eventual: a notification can lag a payment by seconds. This is the intended lesson (Saga/choreography is the next step, CO-0069).

## What is NOT extracted yet (the next tickets)
Payment, booking, subscription, support, streaming and identity are still inside core-service, sharing one database and transactions. They are strongly
coupled (a payment confirmation updates recharge/booking/subscription rows in one transaction), so splitting them is a **design exercise**, not a refactor:
you will need sagas, an anti-corruption layer and data migration. See docs/tickets (Level 7, CO-0065 … CO-0075).

## Adding the next service (checklist)
1. Define its API and events first (docs/events). 2. New module `services/<name>` + Dockerfile arg + compose entry + gateway route. 3. Own database + Flyway.
4. Outbox/consumer for each integration point; idempotency keys everywhere. 5. Contract tests on both sides. 6. Copy the data (scripted, re-runnable). 7. Switch the gateway route; keep the old path until verified. 8. Delete the old code and (later) the legacy tables in a separate migration.
