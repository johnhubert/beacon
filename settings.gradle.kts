rootProject.name = "beacon"

include(
    "common:data-model",
    "common:stateful-client",
    "common:congress-client",
    "tools:congress-cli",
    "backend:rest-service",
    "backend:sse-service",
    "services:ingest-usa-fed",
    "tests:congressgov-integration"
)
