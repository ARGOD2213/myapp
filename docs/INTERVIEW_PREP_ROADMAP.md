# 6-month plan: Java full-stack interview readiness (target: senior-ish, 20 LPA+)

Use ConnectOne as the vehicle: every topic below maps to something you can **run, break, fix and explain** in this repository. Interviews at this level test
(1) fundamentals you can reason about out loud, (2) one real project you can defend in depth, (3) system-design thinking, (4) working habits (Git, tests, CI, on-call).
Do not memorise answers; keep a notebook of *what you saw happen* — specific numbers and incidents are what make an answer credible.

## Weekly rhythm (about 12-15 h/week)
Mon-Thu: 1.5-2 h focused work on the current ticket/topic. Fri: write up what you learned (engineering-report template). Sat: 3 h build/debug session. Sun: 1 h mock interview (explain a design out loud, time-boxed).

## Months 1-2 — Foundations through real tickets (Levels 1-3)
| Weeks | Do | You should be able to explain |
|---|---|---|
| 1 | docs/ENGINEERING_ONBOARDING.md Day 1-5; trace a login, recharge, payment callback | request lifecycle in Spring MVC + Security filter chain; what `@Transactional` does and does not do |
| 2-3 | Level 1-2 tickets (CO-0001...0026): SQL archaeology on the dataset | joins, indexes, `EXPLAIN ANALYZE`, why a query is slow; reading logs with a correlation id |
| 4-6 | Level 3 bug fixes (CO-0027...0038), one PR each with a failing test first | JPA vs JDBC trade-offs, validation, exception handling, idempotency, equals/hashCode, `BigDecimal` money |
| 7-8 | Java depth alongside: collections internals (HashMap, ConcurrentHashMap), streams, `CompletableFuture`, threads/executors, memory model basics, GC basics | tie each to code you touched (e.g. why the outbox relay is single-threaded) |

## Months 3-4 — Features, performance, concurrency (Levels 4-5)
| Weeks | Do | You should be able to explain |
|---|---|---|
| 9-12 | Level 4 features (refund approval CO-0039, pagination CO-0040, auto-renew CO-0042...) with API + migration + UI + tests | REST design, DTOs, pagination, API versioning, Flyway expand/contract, testing pyramid |
| 13-14 | Level 5 performance (CO-0051...0058): find the missing index, the deadlock, the slow search | locks and isolation levels, deadlock cause and fix (lock ordering), connection pool sizing, caching and invalidation |
| 15-16 | Concurrency tests: extend `PostgresConcurrencyAndConstraintTests` | race conditions, optimistic vs pessimistic locking, unique constraints as the last line of defence |

## Months 5-6 — Microservices, DevOps, system design (Level 6-7)
| Weeks | Do | You should be able to explain |
|---|---|---|
| 17-18 | Read docs/MICROSERVICES.md; run the Kafka-down drill; trace one correlation id across 3 services | why microservices (and when NOT to), outbox, at-least-once + idempotency, consumer groups/partitions/ordering, DLT |
| 19-20 | CO-0066 rate limiting, CO-0067 circuit breaker, CO-0068 tracing | resilience patterns, timeouts/retries, observability (logs/metrics/traces) |
| 21-22 | CO-0073 Kubernetes (kind), CO-0075 blue/green, `deploy-local.ps1`; read the CI/CD workflows | rolling vs blue/green vs canary, health probes, rollback, immutable images, 12-factor config, secrets |
| 23-24 | CO-0065 (extract support-service) or CO-0069 (saga) as your capstone; AWS: CO-0074 | data ownership, eventual consistency, saga, API gateway, service discovery, IAM least privilege, VPC basics |

## Topics checklist (tick when you can teach it on a whiteboard)
- Java: OOP/SOLID, collections, generics, streams, concurrency (`synchronized`, locks, executors, virtual threads), JVM memory/GC, exceptions, immutability.
- Spring: IoC/DI, bean lifecycle, AOP/proxies (why `@Transactional` self-invocation fails), Boot auto-configuration, Security (filter chain, JWT), Data access, validation, actuator.
- SQL/DB: normalisation, indexes (B-tree, partial, composite order), transactions/isolation, locks, `EXPLAIN`, N+1, migrations (Flyway).
- Messaging: Kafka topics/partitions/consumer groups/offsets, delivery semantics, outbox, DLT.
- Architecture: monolith vs microservices, bounded contexts, API gateway, saga, CQRS (know when), caching, rate limiting, idempotency, observability.
- DevOps: Git branching/PR flow, CI/CD, Docker (layers, multi-stage, healthchecks), Compose, Kubernetes basics, Terraform basics, AWS core (VPC, EC2/ECS, RDS, S3, CloudFront, IAM, CloudWatch).
- Frontend: React hooks/state, routing, API client with token refresh, error/loading states, accessibility basics, testing.

## How to present ConnectOne in an interview (2-minute version)
"A telecom-style platform: gateway, a core service for payments/recharge/booking, and a notification service fed through Kafka with a transactional outbox. I did the work as a team would:
tickets, branches, PRs, CI on real PostgreSQL and embedded Kafka. Examples I can go deep on: a deadlock between booking cancel and payment confirm that I reproduced and fixed by standardising lock order;
a missing foreign-key index that made recharge history do a sequential scan of 50k payments; how the system behaves when Kafka is down (API keeps working, events wait in the outbox, ~17 s to drain);
and the trade-offs I accepted (shared JWT secret between services, at-least-once delivery). I deployed it locally with Docker Compose with automatic rollback, and wrote Terraform for AWS."
(Only claim what you have actually done: complete the tickets first; the answer key is `docs/_ANSWER_KEY.md`.)

## Likely questions to rehearse with this codebase
1. Walk me through what happens when a customer pays. Which operations are atomic? 2. How do you prevent double charging? 3. Two users book the same seat; what happens?
4. A query got slow after a data increase: how do you investigate? 5. Kafka delivers the same message twice: what breaks, what protects you? 6. Why an outbox instead of publishing inside the transaction?
7. How would you roll back a bad deploy? What about a bad migration? 8. How do services authenticate each other and users? What are the weaknesses of your approach?
9. When would you NOT split a service out? 10. How would you scale reads / handle 10x traffic? 11. Describe your testing strategy and one test that caught a real bug (the H2 -> PostgreSQL bugs are good ones).

## Habits that interviewers notice
Small PRs with tests, written reasoning, measuring before optimising, knowing the limits of what you built (docs/KNOWN_LIMITATIONS.md is honest on purpose), and being able to say "I don't know, here is how I would find out".
