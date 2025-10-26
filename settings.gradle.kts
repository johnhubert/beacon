rootProject.name = "beacon"

include(
    "common:data-model",
    "backend:rest-service",
    "backend:sse-service",
    "services:ingest-usa-fed"
)
