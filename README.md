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
- `services/rest-officials/` &mdash; REST APIs consumed by the UI
- `services/auth-service/` &mdash; Authentication endpoints that issue JWTs
- `services/sse-service/` &mdash; Server-Sent Events publisher streaming live data
- `nginx.conf` &mdash; Gateway config that serves the UI and proxies `/api`
- `docker-compose.yml` &mdash; Builds and runs the entire stack

Learn more about adding backend services in `services/README.md`.

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
| rest-officials | 8081 | http://localhost:8081/api/officials      |
| auth-service  | 8085 | http://localhost:8085/api/auth/demo       |
| SSE API  | 8082        | http://localhost:8082/api/events (stream)|
| mongo    | 27017       | Stores stateful public official data     |

> Tip: the compose file defaults `BEACON_AUTH_JWT_SECRET` to `insecure-dev-secret`. Override this and other auth
> environment variables by exporting them in your shell before running `docker compose up` (e.g.
> `export BEACON_AUTH_JWT_SECRET=$(openssl rand -hex 32)`).

During development you can enable username/password demo access by setting `BEACON_AUTH_DEV_MODE=true` (this is the
default in `docker-compose.yml`). When disabled, the `/api/auth/demo` endpoint will return `403` and the mobile client
will hide the demo login form.

The nginx gateway handles:

- `/` → React Native web bundle
- `/api/officials/**` → rest-officials microservice
- `/api/auth/**` → auth-service microservice
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
# Pick one of the following depending on how you are testing:
# Expo Go on physical device (recommended for LAN hot reload)
export EXPO_PUBLIC_API_BASE_URL="http://192.168.x.x:8080"  # replace with your host machine's IP
# Optional toggles for the TypeScript UI
export EXPO_PUBLIC_DEV_MODE="true"                          # shows demo login when true
export EXPO_PUBLIC_FEATURED_OFFICIAL_SOURCE_ID=""           # optionally force a specific official
npm run start
```

Metro starts in LAN mode (`expo start --host lan`) on port `8083`, which avoids clashing with the
`rest-officials` service exposed by `docker compose` on `8081`. Expo Go on the same Wi-Fi can hot reload
the app while talking to the Dockerized backend through nginx. For iOS/Android simulators on the
same machine, use `npm run start:local` and set `EXPO_PUBLIC_API_BASE_URL="http://localhost:8080"`.
When collaborating remotely, fall back to `npm run start:tunnel`. Press `w` to launch the web preview.

The refreshed login flow now only exposes the `demo/demo` credentials in development. The home tab
pulls a random official from `/api/officials`, applies the new purple (#440e8e)
palette, and displays placeholder BIG Score metrics until the scoring API is ready.

Run a quick type-check before committing:

```bash
cd ui/react-native
npx tsc --noEmit
```

The production Docker image builds static web assets via `npm run build:web`
(`expo export --platform web --output-dir dist`) and packages them with a lightweight HTTP
server. nginx proxies requests back to this container.

## Spring Boot services

Each backend module is part of the shared Gradle build (Spring Boot 3.3 / JDK 21). Build or
run them individually from the repo root, for example:

```bash
./gradlew :services:rest-officials:bootRun
```

To build every module (including generated protobuf classes) run:

```bash
./gradlew build
```

Both services expose `/actuator/health` and are configured to match the
container ports used by Docker Compose.

### API documentation

Springdoc-generated Swagger UIs are available once the services are running:

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|--------------|
| rest-officials | http://localhost:8081/swagger-ui/index.html | http://localhost:8081/v3/api-docs |
  | auth-service | http://localhost:8085/swagger-ui/index.html | http://localhost:8085/v3/api-docs |
  | sse-service | http://localhost:8082/swagger-ui/index.html | http://localhost:8082/v3/api-docs |

### Development login flow

The auth service now issues a signed JWT and sets a `BEACON_DEV_SESSION` cookie when you authenticate with
the `demo/demo` credentials. The React Native client stores the same session locally so native builds and
Expo Go continue to work offline, but the cookie lets the web build reuse the session automatically. No
Google OAuth configuration is required for local development.

### JWT signing key management

The auth service signs access tokens with the value provided in `BEACON_AUTH_JWT_SECRET`.

1. Generate a strong secret locally (32+ random bytes encoded as hex):
   ```bash
   openssl rand -hex 32 > jwt-secret.txt
   ```
2. Add the value to `gradle.properties` **and make it available to docker-compose** (gradle properties are not read by Docker):
   ```properties
   BEACON_AUTH_JWT_SECRET=your-64-character-hex-secret
   ```
   Either export it in your shell (`export BEACON_AUTH_JWT_SECRET=$(cat jwt-secret.txt)`) or place it in a `.env` file that docker-compose can read.
3. In production, store the secret in a managed vault (e.g. AWS Secrets Manager or SSM Parameter Store) and inject it into the container environment at deploy time. Never commit the secret to source control.
4. If the variable is omitted, the service generates an ephemeral dev-only key on startup. This is convenient for quick demos, but tokens become invalid across restarts, so always configure a persistent secret for shared environments.
