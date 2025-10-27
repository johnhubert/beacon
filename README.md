# Beacon

Scaffolding for the Beacon civic accountability platform, organized as a
monorepo with a React Native (Expo) UI and Spring Boot microservices.

##  Platform Overview

Beacon integrates verified civic information, accountability metrics, civic tools, and cultural mechanics into a
single mobile-native experience — designed to turn awareness into action and participation into habit.
Signal LayerTM (Real-Time Facts)
Beacon turns scattered civic information into one clear, verifiable view — personalized by issue, location, and user priority
(including records, verified journalism, verified social accounts, and civic events).

### BIG ScoreTM Accountability
A proprietary, nonpartisan rating system that measures alignment between elected officials’ promises, public statements,
and legislative behavior.

### Civic Tools
Dashboards, flash polls, sentiment tracking, and personalized alerts — built for comprehension, virality, and continuous
engagement.

### Polling & Sentiment Engine
Behavioral polling and real-time sentiment analysis generate a dynamic civic signal — powering BIG Score updates,
personalized insights, and high-margin data products for campaigns, institutions, and researchers.

### Community Layer
Civic Clubs, gamified challenges, creator-led initiatives, and social sharing drive interaction, motivation, and collective
participation.

### Candidate Engagement
Verified profiles, live Q&As, and scalable town halls foster transparency, direct connection, and trust between candidates
and constituents.
Beacon functions as both civic trust infrastructure and a platform for participation — bridging the gap between passive
awareness and active civic life.

## Repository layout

- `ui/react-native/` &mdash; Expo React Native app that can run on devices via
  Expo Go or build to static web artifacts for nginx
- `backend/rest-service/` &mdash; REST APIs consumed by the UI
- `backend/sse-service/` &mdash; Server-Sent Events publisher streaming live data
- `nginx.conf` &mdash; Gateway config that serves the UI and proxies `/api`
- `docker-compose.yml` &mdash; Builds and runs the entire stack

Learn more about adding backend modules in `backend/README.md`.

## Prerequisites

- Docker + Docker Compose v2
- Node 20+ and npm (only if you want to run Expo locally outside Docker)
- Java 21+ (for direct Spring Boot development)
- Gradle 9.1 (provided via the included `./gradlew` wrapper)

### API credentials

Sensitive credentials must never be committed to the repository. To work with third-party
integrations locally (e.g., Congress.gov), copy the template file and keep the real key in your
untracked `gradle.properties`:

```bash
cp gradle.properties.example gradle.properties
echo "API_CONGRESS_GOV_KEY=your-real-key" >> gradle.properties
```

Gradle automatically reads the properties file from the repo root. The file is ignored by Git, so
your key stays local. For CI environments, export the same value as an environment variable instead
of writing it to disk.

## Running everything with Docker

```bash
docker compose up --build
```

Services:

| Service  | Port (host) | Notes                                    |
|----------|-------------|------------------------------------------|
| nginx    | 8080        | http://localhost:8080 serves the UI      |
| frontend | 3000        | Static build served via `serve`          |
| REST API | 8081        | http://localhost:8081/api/health         |
| SSE API  | 8082        | http://localhost:8082/api/events (stream)|
| mongo    | 27017       | Stores stateful public official data     |

The nginx gateway handles:

- `/` → React Native web bundle
- `/api/**` → REST Spring Boot service
- `/api/events` → SSE Spring Boot service with 1 Hz heartbeats

Kafka now runs in a single-node KRaft (ZooKeeper-free) mode. The broker auto-creates topics with 6 partitions (matching the old manual init step) the first time producers publish. If you need to reset state completely, run `docker compose down --volumes` to drop the embedded log directory and let the broker re-format itself on the next startup.

### Stateful storage

- A `mongo` service (MongoDB 7.x) is part of the compose stack and persists data in the `mongo-data` volume.
- Shared access happens through the new `common/stateful-client` Gradle module. It exposes repositories for legislative bodies and public officials plus a `MongoStatefulClient` that all Spring services can reuse.
- Configuration is zero-touch for local development; the client defaults to `mongodb://mongo:27017/accountability_stateful`. Override with environment variables:
  - `STATEFUL_MONGO_URI` (takes precedence) or the trio `STATEFUL_MONGO_HOST`, `STATEFUL_MONGO_PORT`, `STATEFUL_MONGO_DATABASE`.
  - Optional TLS files: set `STATEFUL_MONGO_TLS_CA_FILE` and `STATEFUL_MONGO_TLS_CERT_KEY_FILE` when mutual TLS secrets are mounted.
- Spring Boot services can disable the shared client (e.g., during tests) via `stateful.mongo.enabled=false`.

## Developing the UI with Expo

```bash
cd ui/react-native
npm install
export EXPO_PUBLIC_API_BASE_URL="http://localhost:8080"
npm run start
```

Use the QR code in the terminal with Expo Go on iOS/Android, or press `w` to
launch the web preview. To share over your LAN, run `npm run start:lan`.

The production Docker image builds static web assets via `npm run build:web`
(`expo export --platform web --output-dir dist`) and packages them with a lightweight HTTP
server. nginx proxies requests back to this container.

## Spring Boot services

Each backend module is part of the shared Gradle build (Spring Boot 3.3 / JDK 21). Build or
run them individually from the repo root, for example:

```bash
./gradlew :backend:rest-service:bootRun
```

To build every module (including generated protobuf classes) run:

```bash
./gradlew build
```

Both services expose `/actuator/health` and are configured to match the
container ports used by Docker Compose.
