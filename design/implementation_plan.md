# Beacon Platform Implementation Plan

> Track progress at the epic/phase level using GitHub-style checkboxes. Each phase represents an executable release train that can ship independently.

## Epic 1 – Accountability Data Spine (Phase 1)
- [ ] Author the unified public-accountability data model (protobuf + MongoDB mapping) that captures identity, current office + term status, voting record summary, traced statements, and accountability deltas; ensure Java POJOs generated from proto definitions can hydrate Mongo documents without manual mapping layers.
- [ ] Stand up the minimal ingestion pipeline: federal-source collector microservice, Kafka cluster with identity-hash partitioning rules, and a Mongo-backed profile-state updater that consumes protobuf events to maintain one document per official.
- [ ] Deliver an initial React Native UI surface that lists officials, exposes their latest position, votes, and accountability metrics, and reads from the Mongo-backed profile API.
- [ ] Document the end-to-end flow (federal feed → Kafka → Mongo → UI) plus non-functional guardrails (latency, retry policy, monitoring) required for a production-ready public preview.

_Companion drilldown: `implementation_plan_phase_1.md`_

## Epic 2 – Insight Expansion & Platform Hardening (Phase 2)
- [ ] Add additional federal/state feeds and normalize them into the same protobuf contract; introduce automated validation + deduplication rules across sources.
- [ ] Build the first scoring/metrics service that compares statements vs actions, surfaces variances, and publishes enriched accountability metrics to Kafka + Mongo.
- [ ] Extend observability across the ingestion and scoring services (structured logging, traces, alerting) and add data quality dashboards plus backfill tooling.

## Epic 3 – Cloud Deployment & Reliability Foundations (Phase 3)
- [ ] Translate the on-prem/docker stack into AWS infrastructure-as-code (Terraform/CDK) covering VPC, MSK, MongoDB Atlas or DocumentDB, ECS/EKS services, and managed gateways.
- [ ] Integrate CI/CD pipelines that build containers, push to ECR, deploy to the target runtime, and run smoke tests against stage/prod environments.
- [ ] Close operational readiness gaps: IAM + secrets strategy, runbooks, disaster recovery procedures, and launch-readiness reviews culminating in Beacon v1 hosted in AWS.

## Epic 4 – Distribution & Scale Readiness (Phase 4+)
- [ ] Layer in CDN/edge caching for official cards and accountability metrics, including invalidation hooks driven by Kafka updates.
- [ ] Prepare regional edge rollouts (multi-region Kafka consumers, geo-aware routing, cache warming) to support future growth beyond the initial user base.
- [ ] Outline advanced roadmap items (partner APIs, embedded widgets, citizen feedback loops) gated behind the stable AWS-hosted v1 release.
