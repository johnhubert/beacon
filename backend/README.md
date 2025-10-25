# Backend Services

This directory houses all backend microservices for the Beacon platform. Each
subdirectory represents an independently deployable Spring Boot module with its
own Dockerfile and runtime configuration.

- `rest-service` &mdash; RESTful APIs consumed directly by the UI
- `sse-service` &mdash; Server-Sent Events publisher that streams live updates

To add another microservice, scaffold a new folder next to these services,
replicate the common Maven/Spring structure, and wire it into
`docker-compose.yml`.
