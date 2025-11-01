# External Integration Tests

This directory collects opt-in integration suites that exercise third-party APIs used by Beacon's
ingestion services. The tests run outside of the main Spring Boot modules so that they can be
invoked on demand (they require real network access and authenticated API calls).

## Congress.gov smoke tests

`tests/congressgov-integration` contains JUnit 5 cases that call the Congress.gov API and map each
response into our shared protobuf models via `common:congress-client`. The suite currently verifies:

1. Legislative bodies (House/Senate) can be listed with populated identifiers.
2. Current House members deserialize into `PublicOfficial` protos with the required fields.
3. A random member detail request returns a fully populated record.

### Prerequisites

- A valid Congress.gov API key (free via api.data.gov). Copy `gradle.properties.example` from the
  repo root to `gradle.properties` and insert your key (`CONGRESS_API_KEY=...`). The file is
  `.gitignore`d to prevent accidental commits. You may also export the key as an environment
  variable or pass it inline when invoking Gradle.
- Network access to `https://api.congress.gov`.
- Optional: override the congress session under test via `-PCONGRESS_NUMBER=118`.

### Running the suite

```bash
# Environment variable or gradle.properties
./gradlew :tests:congressgov-integration:test -PintegrationTests=true

# or pass properties inline
./gradlew :tests:congressgov-integration:test -PintegrationTests=true -PCONGRESS_API_KEY=your-key -PCONGRESS_NUMBER=118
```

The test task automatically skips execution if an API key is not supplied, ensuring regular CI builds
remain fast and rate-limit friendly. A convenience alias is also available:

```bash
./gradlew :tests:congressgov-integration:runCongressGovTests -PintegrationTests=true
```

### Rate limits

Congress.gov enforces a limit of 5,000 requests per hour. The suite uses at most four requests (two
paginated member fetches and a single member detail lookup), keeping ample headroom for manual runs.
