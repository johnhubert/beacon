rootProject.name = "beacon"

val modules = mutableListOf(
    "common:data-model",
    "common:stateful-client",
    "common:congress-client"
)

val congressCliDir = file("tools/congress-cli")
if (congressCliDir.isDirectory) {
    modules += "tools:congress-cli"
}

modules += listOf(
    "common:auth",
    "backend:rest-officials",
    "backend:auth-service",
    "backend:sse-service",
    "services:ingest-usa-fed"
)

val optionalTestModules = mapOf(
    "tests:congressgov-integration" to file("tests/congressgov-integration")
)

optionalTestModules
    .filter { (_, dir) -> dir.isDirectory }
    .forEach { (name, _) -> modules += name }

include(*modules.toTypedArray())
