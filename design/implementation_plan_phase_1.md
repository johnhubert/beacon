# Phase 1 Drilldown – Accountability Data Spine

## Scope & Success Criteria
- [ ] Ship an end-to-end slice that ingests one prioritized federal public-records source, emits protobuf events to Kafka, maintains a MongoDB document per elected official, and renders the latest state in the UI.
- [ ] Define and implement the unified accountability data model once, reusing the same protobuf definitions for Kafka payloads, Java POJOs, and Mongo persistence.
- [ ] Guarantee deterministic Kafka partitioning by hashing each official’s identity tuple (e.g., `hash(official_id|jurisdiction|office)`) so all events for an official land on the same partition.
- [ ] Produce operations notes: how to backfill/replay the single source, how to inspect Kafka lag, how to patch a Mongo document, and how to roll services forward.

## Workstream A – Unified Accountability Data Model
- [x] Draft the canonical protobuf schema describing: identity metadata, current office + term, latest voting record summaries, recent statements, accountability deltas (statements vs actions), and data provenance.
- [x] Generate Java POJOs from the proto definitions and validate that the objects serialize directly into MongoDB documents (flatten identifiers, use embedded sub-docs for votes/statements).
- [ ] Document Mongo collection structure (one `official_state` document per official) and required indexes (identity hash, office, geography).
- [ ] Define mapping guidance for downstream systems (UI queries, future scoring engines) so the same schema can be reused without drift.

## Workstream B – Source Collector Microservice
- [x] Scaffold a Spring Boot (or preferred JVM) service that authenticates against the selected federal site, runs scheduled scrapes/pulls, and emits protobuf `OfficialAccountabilityEvent` messages.
- [x] Implement normalization + deduplication rules to ensure each record maps cleanly to the unified schema; drop or quarantine malformed payloads with visibility in logs/metrics.
  - Hourly roster job now refreshes both chambers via Congress.gov, hashes the raw payload for change detection, protects concurrent runs with a Redis NX lock, and upserts officials + hashes into Mongo.
  - Added reusable roster synchronization engine with per-legislative-body locks, refresh interval gating, and persisted refresh timestamps to prevent unnecessary rebuilds on service restart.
- [ ] Package the service with structured logging, health checks, and lightweight metrics (fetch counts, failures, latency) to inform observability from day one.

## Workstream C – Kafka Backbone
- [x] Deploy a local/dev Kafka stack (broker, ZooKeeper/KRaft, schema registry if needed) via docker-compose.
- [x] Create the primary topic (e.g., `official-accountability-events`) with partitions sized for expected growth and configure the identity-hash partitioner.
- [ ] Automate schema registration and topic configuration inside the repo (scripts or IaC) so pipelines can be bootstrapped consistently.
- [ ] Build a mock-producer CLI/tool to publish sample events for local validation and automated tests.

## Workstream D – Stateful Profile Store
- [ ] Implement the `profile-store-service` consumer that reads protobuf events, upserts the matching Mongo document, and enforces “one document per official” semantics.
- [ ] Persist computed accountability metrics (vote counts, agreement deltas, freshness timestamps) within the same document to keep the UI stateless.
- [x] Expose REST endpoints (search, list, detail) that the UI can call; keep responses aligned with the unified model to avoid custom mapping layers.
  - `rest-officials` now surfaces list + detail endpoints; search filtering will follow once OpenSearch lands.
- [ ] Add integration tests that spin up Kafka + Mongo containers, replay sample events, and assert resulting Mongo documents + HTTP responses.

## Workstream E – UI + Experience Surface
- [x] Extend the React Native experience with a list/detail view that surfaces name, office, term status, last votes, and accountability metrics.
- [ ] Wire the UI to the new REST endpoints, add loading/error states, and capture basic analytics/telemetry for usage.
  - API wiring and UX states are live; telemetry events remain to be instrumented.
- [ ] Provide seeded data fixtures or a feature flag so designers/product can demo the experience without the live feed.

## Workstream F – Platform Controls
- [ ] Author runbooks for source ingestion, Kafka replay, and Mongo hotfixes; include CLI snippets and expected outcomes.
- [ ] Configure lightweight alerting (even if via logs + notifications) for scraper failures and consumer lag thresholds appropriate for Phase 1.
- [ ] Capture open questions + risks feeding into Phase 2 (additional sources, scoring service, data quality automation).
