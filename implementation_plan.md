# Beacon MVP Implementation Plan

> Status uses GitHub-style task checkboxes. Complete each story sequentially.

## Story 1 – Define the Backbone
- [ ] Audit and finalize domain glossary (official, feed, signal, BIG Score, card)
- [ ] Align on data contracts between Kafka topics, scoring service, and UI (proto/JSON schema)
- [ ] Extend architecture diagram covering nginx, UI, REST APIs, SSE, Kafka, signal microservices, scoring engine, persistence
- [ ] Document non-functional goals (latency, throughput, SLAs) to guide service sizing

## Story 2 – Platform Bootstrapping
- [ ] Provision shared infrastructure scaffolding: Docker images for Kafka, ZooKeeper, schema registry, shared Postgres
- [ ] Add docker-compose profile that starts Kafka stack alongside existing services
- [ ] Create a `platform-config` module for common configuration (topic names, Avro/JSON schemas)
- [ ] Add CI scripts to build/test each service and run docker-compose smoke tests

## Story 3 – Event Model + Topics
- [ ] Author canonical Kafka topic list (`official_updates`, `official_profiles`, `scoring_results`, etc.)
- [ ] Define Avro/JSON schemas for `OfficialProfileSnapshot`, `OfficialUpdateEvent`, `ScoreCard`
- [ ] Register schemas in schema registry (or create code-based registry if self-hosted)
- [ ] Provide mock-producer CLI to publish sample events for local testing

## Story 4 – Civil Servant Crawler Service (Signal: Public Records)
- [ ] Scaffold `signal-civil-servants-service` (Spring Boot) with scheduled fetch + Kafka producer
- [ ] Implement adapters for prioritized .gov data feeds (e.g., Congress.gov, state portals); start with one federal source
- [ ] Normalize records into `OfficialProfileSnapshot` events, deduplicating by unique identifier
- [ ] Add observability: structured logs, metrics for scrape counts, failure alerts
- [ ] Provide seed configuration file listing initial agencies/endpoints

## Story 5 – Kafka Consumer: Profile Store
- [ ] Create `profile-store-service` to consume `OfficialProfileSnapshot` and persist to Postgres (or document DB)
- [ ] Expose REST endpoints for querying officials (search, by id, by geography) for UI consumption
- [ ] Implement changefeed back to Kafka (`official_profiles` topic) when storage updates occur
- [ ] Add integration tests covering consumer + persistence

## Story 6 – Scoring Engine
- [ ] Scaffold `scoring-service` (Spring Boot) consuming `official_updates` + `official_profiles`
- [ ] Implement initial BIG Score algorithm rules (alignment of votes vs statements, freshness decay)
- [ ] Emit `ScoreCard` events to Kafka and persist latest scores in store accessible by REST API
- [ ] Provide actuator endpoint surfacing scoring metrics (processing lag, errors)

## Story 7 – UI + Gateway Integration
- [ ] Extend REST API to aggregate data from profile store + scoring outputs into UI-friendly responses
- [ ] Update React Native UI to display official profiles, BIG Scores, and live updates
- [ ] Wire SSE feed to stream scoring deltas sourced from Kafka -> SSE bridge service
- [ ] Add loading/error states and telemetry events for UI analytics

## Story 8 – Data Quality + Backfill
- [ ] Build validation jobs that compare ingested profiles against sample truth sets
- [ ] Implement backfill workflow to seed Kafka with historical data for selected officials
- [ ] Document manual review/escalation process for mismatched or missing data

## Story 9 – Operational Readiness + Edge Strategy
- [ ] Add dashboards (Prometheus/Grafana or equivalent) for each microservice and Kafka topics
- [ ] Configure alerting for Kafka consumer lag, scraper failures, scoring backlog
- [ ] Write runbooks for on-call: restarting services, replaying topics, rotating API keys
- [ ] Capture security/privacy review checklist (PII handling, rate limits, data retention)
- [ ] Design Redis + CDN caching layer (CloudFront or Fastly) for official cards, alerts, and BIG Score APIs
- [ ] Implement cache headers/invalidation hooks so Kafka-driven updates purge edge caches in near-real time
- [ ] Load-test cache hit behavior to ensure portable card embeds can scale globally

## Story 10 – AWS Deployment Foundations
- [ ] Pick AWS primitives per tier (ECS/EKS or Lambda for services, MSK for Kafka, ElastiCache for Redis, RDS/Aurora for storage)
- [ ] Author IaC (Terraform/CDK) covering VPC, subnets, security groups, ALB, MSK, Redis, RDS, CloudFront distribution
- [ ] Integrate CI/CD pipelines (GitHub Actions) to build/push images, run ECS/EKS deploys, and invalidate CDN caches
- [ ] Configure IAM + Secrets Manager for least-privilege service-to-service access and credential rotation
- [ ] Document deployment topology diagrams showing traffic paths (CDN/Redis edge → ALB/nginx → services → Kafka/Postgres)

## Story 11 – Launch Checklist
- [ ] Conduct end-to-end test: scrape → Kafka → profile store → scoring → UI
- [ ] Run load test simulating production traffic + feed volume
- [ ] Hold readiness review covering product, infra, data, and ops stakeholders
- [ ] Tag MVP release and document post-launch iterations (partner dashboards, additional feeds, etc.)
