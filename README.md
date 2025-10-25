# Beacon

Scaffolding for the Beacon civic accountability platform, organized as a
monorepo with a React Native (Expo) UI and Spring Boot microservices.

## Overview

Think of Beacon like ESPN for civic accountability: open the app to check an official’s BIG ScoreTM and the story behind it –
with receipts. The same “one clear card” travels across platforms so people encounter trusted, source-linked information
wherever they are.
Core model
Mobile-first app with a complementary web – plus a portable card that can be shared and embedded – and a licensable
data layer for partners. We make accountability legible and trackable without partisan framing.
Where it lives
App and web – deep links resolve to an official source-linked card for each official or issue
How it travels
Embeddable cards – drop into articles, newsletters, and posts, auto-updating with receipts
Creator packaging – short segments by trusted voices that always link back to the official Beacon card
Alerts & digests – push and email for watched officials and issues
Partners
Dashboards – simple, licensable workspaces for newsrooms, universities, NGOs to track BIG Scores, changes & receipts
Broadcast/OTT – clean on-air graphics and a data feed for civic coverage when needed
Trust & controls
Every view links to sources; watermark and attribution preserved; no comment threads inside embeds; plain-English
summaries; localization across major languages
Data layer
Clean endpoints for BIG Score, votes, statements, funding, and receipts – with basic usage tiers and analytics for
institutional partners
Phasing
Phase 1 – App + web, embeddable cards, creator packaging, alerts
Phase 2 – Partner dashboards and simple community tools
Phase 3 – Broadcast feed and broader internationalization
Bottom line
Beacon is an app you can open, a card you can share, and a trusted layer partners can license – one clear card that lines
up words vs deeds with receipts, everywhere people already are

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

The nginx gateway handles:

- `/` → React Native web bundle
- `/api/**` → REST Spring Boot service
- `/api/events` → SSE Spring Boot service with 1 Hz heartbeats

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

Each backend module is a standalone Spring Boot 3.3/JDK 21 project. Build or
run them individually, for example:

```bash
cd backend/rest-service
mvn spring-boot:run
```

Both services expose `/actuator/health` and are configured to match the
container ports used by Docker Compose.

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