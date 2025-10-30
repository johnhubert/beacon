# Cloud Services

The `services/` directory contains every deployable backend workload that powers
Beacon. Each subdirectory is a Spring Boot microservice with its own Dockerfile,
infrastructure configuration, and Gradle module.

Current services:

- `rest-officials/` &mdash; REST APIs consumed by the mobile application and web clients.
- `auth-service/` &mdash; Authentication endpoints that issue and validate session tokens.
- `sse-service/` &mdash; Server-Sent Events publisher used for live update streams.
- `ingest-usa-fed/` &mdash; Ingestion pipeline that captures U.S. federal legislative data.

## Adding a new service

1. Scaffold a new folder inside `services/` and copy the Gradle + Docker
   scaffolding from an existing module as a starting point.
2. Register the module in `settings.gradle.kts` (following the
   `services:<module-name>` convention).
3. Update `docker-compose.yml` if the service should run in the local stack and
   provide a dedicated Dockerfile in the module directory.
4. Keep the module’s README or comments up to date with runtime expectations,
   environment variables, and any external integrations.

By keeping every backend service under a single directory, we maintain a
consistent structure for local development, CI pipelines, and deployment
tooling.
