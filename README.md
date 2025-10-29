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
- `backend/rest-officials/` &mdash; REST APIs consumed by the UI
- `backend/auth-service/` &mdash; Authentication endpoints that issue JWTs
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
# When testing with Expo Go on a device or emulator, point to your LAN IP
export EXPO_PUBLIC_API_BASE_URL="http://192.168.x.x:8080"  # replace with your machine's IP
npm run start
```

`npm run start` binds Metro to your LAN IP so Expo Go on physical devices can connect
to the API gateway through nginx. If you are using the iOS/Android emulators on the same
machine, run `npm run start:local` and set `EXPO_PUBLIC_API_BASE_URL="http://localhost:8080"`
to keep the host at `localhost`. For remote collaborators you can fall back to
`npm run start:tunnel`. Press `w` in the terminal to launch the web preview.

The production Docker image builds static web assets via `npm run build:web`
(`expo export --platform web --output-dir dist`) and packages them with a lightweight HTTP
server. nginx proxies requests back to this container.

## Spring Boot services

Each backend module is part of the shared Gradle build (Spring Boot 3.3 / JDK 21). Build or
run them individually from the repo root, for example:

```bash
./gradlew :backend:rest-officials:bootRun
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

### Google Sign-In setup for local development

1. Create a Google Cloud project (https://console.cloud.google.com/), enable the **Google People API**, and open **APIs & Services → Credentials**.
2. Create three OAuth 2.0 client IDs:
   - **Web application** – add origins `http://localhost`, `http://localhost:3000`, `http://localhost:8080` (no trailing slash or path) and a redirect of the form `https://auth.expo.io/@your-expo-username/beacon-ui`. Replace `your-expo-username` with the account returned by `npx expo whoami` (use `expo login` first if needed). The redirect must not include a trailing slash.
   - **Android** – use the package name `com.hd_johnny.beaconui`. For the debug SHA-1 you can run `keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore` (password `android`).
   - **iOS** – use the bundle identifier `com.hd-johnny.beaconui`.
3. Copy the generated client IDs (and the web client secret) into your local `gradle.properties` (git-ignored):
   ```properties
   BEACON_AUTH_GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   BEACON_AUTH_GOOGLE_ANDROID_CLIENT_ID=your-android-client-id.apps.googleusercontent.com
   BEACON_AUTH_GOOGLE_IOS_CLIENT_ID=your-ios-client-id.apps.googleusercontent.com
   BEACON_AUTH_GOOGLE_CLIENT_SECRET=your-google-client-secret
   BEACON_AUTH_JWT_SECRET=generate-a-long-random-string
   BEACON_AUTH_DEV_MODE=true
   ```
4. Before running Docker compose, export the same values so the containers can read them:
   ```bash
   export BEACON_AUTH_GOOGLE_WEB_CLIENT_ID=$(grep BEACON_AUTH_GOOGLE_WEB_CLIENT_ID gradle.properties | cut -d'=' -f2)
   export BEACON_AUTH_GOOGLE_ANDROID_CLIENT_ID=$(grep BEACON_AUTH_GOOGLE_ANDROID_CLIENT_ID gradle.properties | cut -d'=' -f2)
   export BEACON_AUTH_GOOGLE_IOS_CLIENT_ID=$(grep BEACON_AUTH_GOOGLE_IOS_CLIENT_ID gradle.properties | cut -d'=' -f2)
   export BEACON_AUTH_GOOGLE_CLIENT_SECRET=$(grep BEACON_AUTH_GOOGLE_CLIENT_SECRET gradle.properties | cut -d'=' -f2)
   export BEACON_AUTH_JWT_SECRET=$(grep BEACON_AUTH_JWT_SECRET gradle.properties | cut -d'=' -f2)
   export BEACON_AUTH_DEV_MODE=$(grep BEACON_AUTH_DEV_MODE gradle.properties | cut -d'=' -f2)
   ```
5. Start the stack with `docker compose up --build`. The React Native app will now display the Google sign-in button using the server-provided configuration while still allowing `demo/demo` credentials when `BEACON_AUTH_DEV_MODE=true`.
   - When testing via **Expo Go**, the app uses the **web client ID** under the hood (regardless of device). Ensure the web client entry is configured even if you’re only validating on Android/iOS simulators.

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
