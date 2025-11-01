# Common Data Model (CDM)

## 1. Purpose and Scope

The Beacon Common Data Model defines the canonical structures used to represent public officials, legislative bodies, votes, and downstream accountability signals. The CDM is the single source of truth for:

- Stateful persistence (MongoDB collections managed by `common/stateful-client`).
- Event payloads emitted on the accountability stream.
- REST, export, and analytics schemas that consume the same normalized entities.

This document supersedes data-shape details previously embedded in design docs (see `design/ingestion_plan.md` for ingestion-specific strategy). All implementations must align with the field definitions recorded here.

## 2. Conventions and Shared Fields

| Field | Applies To | Type | Description | Notes |
| --- | --- | --- | --- | --- |
| `uuid` | All stateful objects and events | String (UUID v4) | Beacon-generated global identifier. | Never reuse or recycle. Stored as `_id` in Mongo. |
| `source_id` | Stateful objects, events, votes | String | Upstream identifier from the originating system (e.g., Bioguide ID, LegiScan key). | Do not assume global uniqueness. |
| `ingestion_source` | Events | String | Human-readable label describing where the event was produced (e.g., `congress.gov`). | Required on `OfficialAccountabilityEvent`. |
| `captured_at` | Events | Timestamp (UTC) | Moment the event snapshot was produced. | Stored as `google.protobuf.Timestamp`, mapped to ISO-8601 in REST. |
| `last_refreshed_at` | Stateful officials | Timestamp (UTC) | When the official record was last synchronized. | Stored as Mongo `Date`. |
| `partition_key` | Events | String | Deterministic routing key for downstream consumers. | Generally `${officialUuid}::${chamber}`. |
| `jurisdiction_code` | Legislative bodies | String | ISO Alpha-2 or custom region code identifying the country/province. | Enables international expansion. |

**Timestamp handling**

All persisted timestamps are stored in UTC. Mongo uses `Date` objects, protobuf messages serialize as `google.protobuf.Timestamp`, and REST responses return ISO-8601 strings.

## 3. Stateful Domain Objects (MongoDB)

Stateful data lives in the following collections:

- `legislative_bodies`
- `public_officials`
- `legislative_body_votes` (Voting records + member votes)

### 3.1 LegislativeBody

| Proto Field | Mongo Field | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `uuid` | `_id` | String (UUID) | Yes | Global identifier for the legislative chamber or body. |
| `source_id` | `source_id` | String | Yes | Upstream identifier (e.g., `US-HOUSE-118`). |
| `jurisdiction_type` | `jurisdiction_type` | Enum (`JurisdictionType`) | Yes | Government level (FEDERAL, STATE, PROVINCIAL, SUPRANATIONAL, MUNICIPAL). |
| `jurisdiction_code` | `jurisdiction_code` | String | Yes | ISO Alpha-2 country code or scoped region (e.g., `US`, `US-TX`). |
| `name` | `name` | String | Yes | Official chamber name. |
| `chamber_type` | `chamber_type` | Enum (`ChamberType`) | Yes | UNICAMERAL / LOWER / UPPER / COMMITTEE. |
| `session` | `session` | String | Optional | Legislative session (e.g., `118`). |
| `roster_last_refreshed_at` | `roster_last_refreshed_at` | Timestamp | Optional | UTC time of last roster sync. |
| `last_vote_ingested_at` | `last_vote_ingested_at` | Timestamp | Optional | UTC time when the latest vote was processed. |

### 3.2 PublicOfficial

| Proto Field | Mongo Field | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `uuid` | `_id` | String (UUID) | Yes | Global official identifier. |
| `source_id` | `source_id` | String | Yes | Upstream identifier (Bioguide ID, MEP ID, etc.). |
| `legislative_body_uuid` | `legislative_body_uuid` | String (UUID) | Yes | Foreign key to `LegislativeBody.uuid`. |
| `full_name` | `full_name` | String | Yes | Canonical name. |
| `party_affiliation` | `party_affiliation` | String | Yes | Party or caucus code. |
| `role_title` | `role_title` | String | Yes | Jurisdiction-specific role title (Representative, MP, MEP). |
| `jurisdiction_region_code` | `jurisdiction_region_code` | String | Yes | ISO state/province code or country code. |
| `district_identifier` | `district_identifier` | String | Conditional | District/constituency reference (numeric or textual). |
| `term_start_date` | `term_start_date` | Timestamp | Optional | UTC date when current term started. |
| `term_end_date` | `term_end_date` | Timestamp | Optional | UTC date when current term ended/ends. |
| `office_status` | `office_status` | Enum (`OfficeStatus`) | Yes | ACTIVE, VACANT, SUSPENDED, RETIRED. |
| `biography_url` | `biography_url` | String | Optional | Source biography link. |
| `photo_url` | `photo_url` | String | Optional | Portrait URL. |
| `version_hash` | `version_hash` | String | Optional | SHA-1 digest of upstream payload (change detection). |
| `last_refreshed_at` | `last_refreshed_at` | Timestamp | Optional | Last ingestion timestamp. |
| `attendance_summary` | `attendance_summary` | Embedded doc | Optional | See table 3.2.1. |
| `attendance_history` | `attendance_history` | Array of docs | Optional | Historical snapshots (max 24 entries). |

**3.2.1 AttendanceSummary (embedded)**

Fields: `sessions_attended`, `sessions_total`, `votes_participated`, `votes_total`, `presence_score`, `participation_score`. All integers; missing values default to zero.

**3.2.2 AttendanceSnapshot (embedded)**

| Field | Type | Description |
| --- | --- | --- |
| `period_label` | String | Human-readable descriptor (e.g., `Last 30d`). |
| `period_start` / `period_end` | Timestamp | Window bounds (optional). |
| `sessions_attended`, `sessions_total`, `votes_participated`, `votes_total`, `presence_score`, `participation_score` | Integers | Metrics scoped to the period. |

### 3.3 VotingRecord

Stored in `legislative_body_votes`.

| Proto Field | Mongo Field | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `uuid` | `_id` | String (UUID) | Yes | Deterministic vote identifier. |
| `source_id` | `source_id` | String | Yes | Upstream roll-call identifier (e.g., `US-HOUSE-S01-R001`). |
| `legislative_body_uuid` | `legislative_body_uuid` | String (UUID) | Yes | Foreign key to `LegislativeBody`. |
| `vote_date_utc` | `vote_date_utc` | Timestamp | Optional | Scheduled vote time. |
| `subject_summary` | `subject_summary` | String | Yes | Question or short title. |
| `bill_reference` | `bill_reference` | String | Optional | Normalized bill identifier (e.g., `HR 1234`). |
| `bill_uri` | `bill_uri` | String | Optional | Source legislation URL. |
| `roll_call_reference` | `roll_call_reference` | String | Yes | Triplet `congress-session-rollCall`. |
| — | `congress_number` | Integer | Optional | Stored metadata for filtering. |
| — | `session_number` | Integer | Optional | Stored metadata for filtering. |
| — | `roll_call_number` | Integer | Optional | Stored metadata for filtering. |
| — | `vote_result` | String | Optional | Outcome text (e.g., `Passed`). |
| — | `vote_type` | String | Optional | Vote classification from source. |
| — | `source_data_url` | String | Optional | Raw data link for diagnostics. |
| — | `legislation_type` | String | Optional | Bill type (e.g., `HR`, `SRES`). |
| — | `legislation_number` | String | Optional | Bill number. |
| — | `summary` | String | Optional | AI-generated paragraph summary. Absent until enrichment completes. |
| `member_votes` | `member_votes` | Array of docs | Optional | See section 3.3.1. |
| `update_date_utc` | `update_date_utc` | Timestamp | Optional | Upstream update time. |

**3.3.1 MemberVote (embedded)**

| Proto Field | Mongo Field | Type | Description |
| --- | --- | --- | --- |
| `uuid` | `uuid` | String (UUID) | Deterministic per vote + official. |
| `source_id` | `source_id` | String | Upstream member identifier (Bioguide). |
| `official_uuid` | `official_uuid` | String (UUID) | Optional link to `PublicOfficial`. |
| `voting_record_uuid` | `voting_record_uuid` | String (UUID) | Back-reference to parent vote. |
| `vote_position` | `vote_position` | String (enum name) | YEA / NAY / ABSENT / NOT_VOTING / VOTE_POSITION_UNSPECIFIED. |
| `group_position` | `group_position` | String | Party whip or caucus guidance (currently empty). |
| `notes` | `notes` | String | Raw upstream label when no normalized value exists. |

## 4. Event Domain Objects

Events are published via Kafka using the protobuf messages in `accountability.proto`.

### 4.1 OfficialAccountabilityEvent

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `uuid` | String (UUID) | Yes | Event identifier. |
| `source_id` | String | Yes | Composition of domain + `PublicOfficial.source_id`. |
| `captured_at` | Timestamp | Yes | Event production time. |
| `ingestion_source` | String | Yes | Logical name of the ingest pipeline. |
| `partition_key` | String | Yes | Key used for Kafka routing. |
| `legislative_body` | `LegislativeBody` message | Yes | Snapshot of the body context. |
| `public_official` | `PublicOfficial` message | Yes | Snapshot of the official state. |
| `voting_records` | `repeated VotingRecord` | Optional | Current vote payloads attached to the event. |
| `accountability_metrics` | `repeated AccountabilityMetric` | Optional | Derived scoring data. |

### 4.2 AccountabilityMetric

| Field | Type | Description |
| --- | --- | --- |
| `uuid` | String (UUID) | Stable identifier for metric definition or claim. |
| `source_id` | String | Upstream or analytic reference. |
| `name` | String | Display name (e.g., `Attendance Score`). |
| `score` | Double | Numeric value (0–100). |
| `methodology_version` | String | SemVer or free-form version tag. |
| `details` | String | Optional description, rationale, or JSON blob. |

## 5. Enumerations

| Enum | Values |
| --- | --- |
| `JurisdictionType` | `FEDERAL`, `STATE`, `PROVINCIAL`, `SUPRANATIONAL`, `MUNICIPAL`, `JURISDICTION_TYPE_UNSPECIFIED` |
| `ChamberType` | `UNICAMERAL`, `LOWER`, `UPPER`, `COMMITTEE`, `CHAMBER_TYPE_UNSPECIFIED` |
| `OfficeStatus` | `ACTIVE`, `VACANT`, `SUSPENDED`, `RETIRED`, `OFFICE_STATUS_UNSPECIFIED` |
| `VotePosition` | `YEA`, `NAY`, `ABSENT`, `NOT_VOTING`, `VOTE_POSITION_UNSPECIFIED` |

Use the enum names (not integers) when persisting values to Mongo or exposing REST fields to keep datasets human-readable.

## 6. Internationalization and Regional Metadata

- `jurisdiction_code` (LegislativeBody) captures the country or regional code (ISO Alpha-2 when available). For subnational bodies, append subdivision codes (e.g., `US-TX`).
- `jurisdiction_region_code` (PublicOfficial) carries the official’s electoral region (state, province, country). Use ISO codes or standardized abbreviations.
- `district_identifier` supports both numeric districts (`"12"`) and textual constituency names (`"Leeds North West"`).
- Future non-national bodies (e.g., EU Parliament) should populate `jurisdiction_type = SUPRANATIONAL` and use relevant codes (`"EU"`).

## 7. Mapping Guidelines

1. **Normalization pipeline**: All ingestion agents must map source payloads into the protobuf structures before persistence. Use `common/stateful-client` converters to handle Mongo serialization.
2. **UUID generation**: Deterministically generate vote and member vote UUIDs (`UUID.nameUUIDFromBytes`) when upstream identifiers do not guarantee uniqueness.
3. **Summaries**: The `summary` field on `legislative_body_votes` is populated asynchronously via the Legislation Summary Service. Until present, downstream readers should treat the field as optional.
4. **Version hashes**: `PublicOfficial.version_hash` enables cheap change detection. Agents should compute a digest of the raw upstream JSON.
5. **Event snapshots**: Emit `OfficialAccountabilityEvent` after stateful writes to keep analytic systems synchronized with Mongo.

## 8. References

- Protobuf definitions: `common/data-model/src/main/proto/accountability.proto`
- Mongo converters: `common/stateful-client/src/main/java/com/beacon/stateful/mongo/converter`
- Ingestion services: `services/ingest-usa-fed/src/main/java/com/beacon/ingest/usafed/service`
- Stateful repositories: `common/stateful-client/src/main/java/com/beacon/stateful/mongo`

All future schema changes must update this document alongside the code to maintain a consistent contract across services.
