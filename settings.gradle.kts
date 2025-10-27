rootProject.name = "beacon"

include(
    "common:data-model",
    "common:stateful-client",
    "backend:rest-service",
    "backend:sse-service",
    "services:ingest-usa-fed"
)
