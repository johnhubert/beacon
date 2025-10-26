

# **Technical Blueprint for Global Legislative Transparency Data Platform**

## **I. Strategic Overview and Foundational Data Mapping**

### **A. Project Mandate and Architectural Vision**

The objective of this blueprint is to establish a rigorous, scalable data ingestion pipeline for public officials and their legislative voting records, commencing with the United States Federal government but engineered for seamless expansion to US State and international jurisdictions. The core architectural vision mandates the development of a Common Data Model (CDM) that acts as an abstraction layer, normalizing inherently disparate legislative structures—ranging from the bicameral systems of the US and UK to the supranational political groupings of the European Union—into a cohesive, standardized format.

This architecture is founded on a modular ingestion platform. Specialized *Source Agents* will be developed to handle the proprietary schema variations (e.g., Congress.gov JSON versus LegiScan structured data). These agents are responsible for executing the extraction, transformation, and loading (ETL) process, mapping the source data onto the generic CDM. This methodology ensures uniformity in downstream analytics, irrespective of the data's geographic or governmental origin.

The immediate architectural challenge centers on bridging the differences between highly structured, albeit occasionally unstable, public APIs (such as US Federal and State sources) and the widely variable international legislative models.1 To address this, the data structure prioritizes three atomic concepts: the PublicOfficial entity (the *who*), the VotingRecord entity (the *what* and *when*), and the MemberVote entity (the *how* they voted). This separation ensures referential integrity and facilitates high-performance analytical queries across large, global datasets.

### **B. Initial Architectural Considerations and Trade-offs**

The initial analysis of available public APIs reveals crucial constraints that directly dictate the necessary architecture and ingestion strategy.

#### **1\. The Implication of Beta Status and Rate Limiting**

The most significant constraint for the initial US Federal implementation resides in the limitations of the Congress.gov API. The endpoints responsible for House roll call vote data are explicitly designated as "currently in beta".3 This categorization signifies a substantial risk of schema instability and mandates that the ingestion agent incorporates robust runtime schema validation and advanced fault tolerance mechanisms, as future schema changes could potentially break the ingestion logic without notice.

Furthermore, operational efficiency is highly restricted by the defined rate limit. The Congress.gov API is capped at 5,000 requests per hour (RPH).4 This limit, translating to approximately $1.39$ requests per second, is fundamentally prohibitive for immediate bulk ingestion of the historical record, which spans decades and encompasses tens of thousands of votes. A straightforward approach to pulling all historical votes one-by-one would render the initial data population phase impractical, potentially stretching over months.

The necessary architectural trade-off is the immediate abandonment of a comprehensive historical ingestion strategy via the API. The initial US Federal ingester must be optimized exclusively for **delta loading**—prioritizing the acquisition of data from the current and most recent legislative sessions. To manage the 5,000 RPH ceiling, the platform must integrate sophisticated queuing mechanisms, precise rate-limit awareness, and exponential backoff protocols. For the eventual historical catch-up, continuous monitoring of GPO’s *unspecified* Roll Call Votes resource and other bulk legislative data options is required, as historical ingestion may need to rely on parallel, non-API file transfer pathways.6

#### **2\. Centralizing US State Data through Aggregation**

Expansion to the US state level presents the challenge of integrating data from 50 separate, often non-standardized, legislative systems. The analysis confirms that the LegiScan API provides structured JSON information for legislation and roll call records across all 50 states, in addition to Congress.7 Utilizing LegiScan as the primary data source for US State information provides immediate, standardized access, effectively eliminating the need to design and maintain 50 distinct state-level scrapers or ingestion agents.

While this approach significantly accelerates the US State expansion timeline and reduces initial modeling complexity, it introduces vendor lock-in and a dependency on LegiScan's update schedule and data fidelity. Therefore, LegiScan will be architecturally treated as the primary, high-value source for US State data, while the CDM itself must remain agnostic, allowing for potential integration of primary state sources later if data quality necessitates it.

#### **3\. Defining a Hierarchical Jurisdiction Model**

Global expansion necessitates a data model capable of addressing diverse structural distinctions. Simply using a two-level hierarchy (e.g., Country, Chamber) is insufficient for accurate modeling. For example, US members are tied to Congress, State, and District 3; the UK Parliament distinguishes between the House of Commons and the House of Lords, requiring separate API endpoints for their respective votes 8; and the European Parliament organizes members by transnational political groups and their member states.2

The CDM, therefore, requires a granular LegislativeBody entity that enforces a multi-dimensional hierarchy. This entity must capture the **Jurisdictional** dimension (Federal, State, Supranational), the **Chamber** dimension (Upper, Lower, Unicameral), and the **Regional** dimension (District, Constituency). This layered structure ensures that an official's role is unambiguously defined and traceable regardless of the governing body's complexity.

### **C. Overview of Data Sources by Jurisdiction**

The chosen initial and expansion data sources provide a strong foundation for both immediate US Federal implementation and future global scalability, covering the three required governmental tiers. The following table summarizes the key access parameters.

Table 1: Key Legislative API Catalog and Access Summary

| Jurisdiction | API Service | Base URL Example | Format(s) | Access Requirement | Rate Limit/Restriction | Primary Scope |
| :---- | :---- | :---- | :---- | :---- | :---- | :---- |
| US Federal | Congress.gov API (v3) | https://api.congress.gov/v3/ | JSON, XML | API Key required (Data.gov signup) 3 | 5,000 requests per hour 4 | Member Metadata, Congressional Votes (House/Senate) |
| US State/Consolidated | LegiScan API | https://api.legiscan.com/ | Structured JSON | API Key required (Free registration) 7 | Public API: 30,000 queries/month 7 | Legislation, Roll Call Votes (50 States \+ Congress) |
| International (EU) | HowTheyVote API (Experimental) | https://howtheyvote.eu/api/ | JSON, CSV | Open/Experimental | None specified (Weekly export available) 11 | MEPs, Political Groups, Roll Call Votes |
| International (UK) | Parliament APIs (Members, Votes) | https://members-api.parliament.uk/ | JSON (Implied) | Public, requires license acknowledgment 8 | Not specified | MPs, Lords, Constituency data, Commons/Lords Votes |

## **II. API Catalog and Access Specification: The Ingestion Front-End**

### **A. US Federal Source: Congress.gov API (v3)**

The Congress.gov API, maintained by the Library of Congress (LoC), is the definitive official source for US Federal legislative data, offering responses in both XML and JSON formats.10 For compatibility with modern agentic development and ease of mapping, JSON is the mandated default format.

#### **1\. Authentication and Operational Constraints**

Access to the Congress.gov API requires an API key, which must be secured via the Data.gov signup platform.3 This key must be included in all requests for authorization. As detailed previously, the ingestion architecture must rigorously manage its consumption to adhere to the hard limit of 5,000 RPH.4 Any ingestion attempts exceeding this threshold will result in throttling or potential IP blocking, severely impacting real-time data flow.

#### **2\. Key Endpoints for Officials and Voting Records**

The initial ingestion agent will focus on two primary data streams: Member data for the PublicOfficial entity and house-vote data for the VotingRecord and MemberVote entities.

##### **Member Data Endpoints:**

The structure of member data retrieval relies on unique identifiers and geographical filtering 3:

* GET/member/congress/{congress}: This is used to retrieve a list of members filtered by a specific Congress (e.g., the 118th).  
* GET/member/{bioguideId}: This is the definitive endpoint for retrieving detailed biographical and role information for a single member, with the {bioguideId} serving as the unique source identifier.  
* GET/member/congress/{congress}/{stateCode}/{district}: This endpoint allows for granular geographic filtering, which is necessary for accurately linking an official to their regional representation role.

##### **Roll Call Vote Data Endpoints (Beta):**

The roll call vote endpoints are critical but are currently marked as beta.3 They are accessed via the following structure, with the combination of Congress, Session, and Vote Number forming the composite unique identifier for the vote event:

* GET/house-vote/{congress}/{session}/{voteNumber}: Retrieves the metadata, date, subject, and results totals for a specific roll call vote.3  
* GET/house-vote/{congress}/{session}/{voteNumber}/members: This is the **most critical endpoint** as it returns the detailed breakdown of how each member voted (e.g., YEA, NAY, Not Voting) on the specified roll call.3 This result set is mapped directly to the MemberVote entity.

### **B. US State and Consolidated Source: LegiScan API**

The LegiScan API is indispensable for achieving national coverage in the US expansion phase, as it provides a structured, uniform JSON interface covering all 50 states and Congress.7 This aggregation capability drastically reduces complexity.

Access requires the creation of an account and the generation of an associated API key.7 The free Public API service tier provides 30,000 queries per month, which is sufficient for initial development and proof-of-concept testing but is likely insufficient for continuous, production-level, multi-state monitoring. Scaling the platform to encompass all US states will require budgeting for the introductory (Pull API) or enterprise (Push API) subscription tiers, which offer significantly higher query limits or full database replication.7 The API explicitly provides access to roll call records, confirming its suitability for direct mapping to the CDM's VotingRecord and MemberVote entities.7

### **C. International Prototypes: Extensibility Benchmarks**

The international data sources serve as essential architectural benchmarks to validate the CDM’s structural extensibility, forcing the model to normalize concepts beyond the US legislative framework.

#### **1\. European Parliament Data**

The HowTheyVote.eu API, although designated as "experimental," provides access to European Parliament roll-call votes, including associated schemas for Member, Group, and Country.2 This confirms that the CDM must generalize the US concept of 'Party Affiliation' into a broader PoliticalAffiliation entity capable of accommodating multi-national political groups (e.g., the European People's Party) found in supranational bodies. Furthermore, the availability of a weekly updated dataset export via GitHub 11 suggests a useful architectural redundancy, allowing the platform to manage current data via the API while relying on bulk exports for reliable historical ingestion, mitigating the instability risk of an experimental API.

#### **2\. UK Parliament Data**

The UK Parliament provides several APIs, distinctly separating data for the House of Commons and the House of Lords.8 This explicit separation strongly confirms the necessity of the hierarchical LegislativeBody entity within the CDM to distinguish between legislative chambers within a single national jurisdiction. Specifically, endpoints exist to retrieve members’ voting history (/api/Members/{id}/Voting), constituency details, and election results.9 This demonstrates that the PublicOfficial entity must be capable of supporting detailed tenure metadata and linking to complex regional identifiers (constituency names rather than simple district numbers).

## **III. US Federal Ingestion Implementation Plan (Initial Focus)**

The immediate development effort is dedicated to establishing the CongressGovV3Agent, which must be strategically engineered to operate reliably under the defined 5,000 RPH rate constraint and mitigate risks associated with beta endpoints.

### **A. Phased Ingestion Strategy (Rate-Limited Delta Loading)**

Due to the severe throughput limitations, the initial ingestion must prioritize the most current and relevant data through a phased approach.

#### **Phase 1: Member Metadata Stabilization (Daily/Weekly Schedule)**

The priority in this phase is to establish and maintain a stable roster of the current legislative body. This involves ingesting and updating all members of the currently sitting Congress (e.g., the 118th Congress). The agent will utilize the generalized member list endpoint (GET/member/congress/{congress}) followed by detailed lookups using the GET/member/{bioguideId} endpoint.3 Since member biographical and role data changes infrequently, this phase should operate on a low-frequency schedule (daily or weekly). Given the limited number of Congress members (535 representatives plus senators), this process consumes a minimal fraction of the available 5,000 RPH capacity.

#### **Phase 2: Current Vote Acquisition (High Frequency, Real-Time Focus)**

This phase is the core operational objective: capturing all new roll call votes as close to real-time as possible. The ingestion agent must employ a sophisticated polling mechanism. It will periodically query the metadata endpoint (GET/house-vote/{congress}/{session}) to rapidly identify new vote events based on the unique combination of Congress, Session, and Vote Number.

For every newly identified vote, the agent must execute two sequential, critical detail calls:

1. Fetch the VotingRecord metadata detail (GET/house-vote/{...}/{voteNumber}).  
2. Fetch the MemberVote positional data (GET/house-vote/{...}/{voteNumber}/members).3

Crucially, the scheduling and execution of Phase 2 must be governed by a strict rate limiter that ensures total API usage remains significantly below the 5,000 RPH hard cap. This buffer space is essential to accommodate unexpected latency, necessary exponential backoff during throttling events, and re-tries following schema validation failures inherent to operating against beta endpoints.

#### **Phase 3: Historical Catch-Up (Deferred Strategy)**

Historical data ingestion (data preceding the last two Congresses) cannot proceed via the Congress.gov API due to the rate limit constraint. This phase is logically deferred and can only be activated under two conditions: (1) The Congress.gov API rate limit is significantly increased, or (2) A specific, documented bulk data option for roll call votes becomes available through GPO's data repository or other official sources.6 If forced to proceed via the API, historical ingestion must be throttled to occur only during off-peak hours and must be automatically paused if it interferes with the real-time delta loading (Phase 2).

### **B. Detail: Data Acquisition Flow and Schema Validation**

The ingestion agent must incorporate robust data transformation logic immediately post-retrieval to map the source schema onto the normalized CDM.

#### **Member Data Flow:**

The flow begins by querying the member list, iterating through the list of unique Bioguide IDs.3 For each ID, the detailed record is fetched. Key source fields, such as bioguideId, stateCode, party designation, and Congressional role, are extracted and mapped to the corresponding properties in the PublicOfficial CDM entity (Table 3). The transformation process must include a validation step to ensure that the mapping correctly handles complex role data, such as members who may have served in multiple roles or changed party affiliations within a single Congress.1

#### **Roll Call Vote Data Flow:**

After identifying new votes, the agent performs the detailed fetch operations. The vote metadata is mapped to the VotingRecord CDM entity (Table 4), capturing date, subject, and the combination of Congress/Session/Vote Number used for the source ID. Subsequently, the positional data from the /members endpoint is mapped to the MemberVote CDM entity (Table 4), using the Bioguide ID to resolve the associated PublicOfficial.

A critical component of this process is error handling for beta endpoints.3 If the vote response structure deviates from the expected schema, the agent must not halt the pipeline. Instead, it must log a structured schema validation error, alert the system administrator, and gracefully skip the individual record, ensuring continuous operation for all subsequent, valid vote records.

Table 2: US Federal API Endpoint Specification and Data Requirements (Congress.gov Focus)

| Data Entity | API Type | Endpoint Path | Key Parameters (Input) | Critical Data Fields (Output) | Source Reference |
| :---- | :---- | :---- | :---- | :---- | :---- |
| PublicOfficial (List) | List/Filter | GET/member/congress/{congress} | {congress}, {stateCode}, {district} | Bioguide ID, Name, State, Chamber | 3 |
| PublicOfficial (Detail) | Detail | GET/member/{bioguideId} | {bioguideId} | Full name, Birth Date, Party Affiliation, Roles/Terms | 1 |
| VotingRecord (Metadata) | Beta Detail | GET/house-vote/{congress}/{session}/{voteNumber} | {congress}, {session}, {voteNumber} | Vote ID (Source), Date/Time, Subject/Description, Result totals | 3 |
| MemberVote (Positions) | Beta Detail | GET/house-vote/{congress}/{session}/{voteNumber}/members | {congress}, {session}, {voteNumber} | Member ID, Vote Position (e.g., YEA/NAY/NV), Party totals | 3 |

## **IV. Extensible Data Model Design (CDM: Common Data Model)**

The Common Data Model defines the necessary structure for legislative data normalization, ensuring the architecture is not confined solely to the US Federal system but is inherently extensible to US State and International data sources.

### **A. Core Principles of Extensibility**

The CDM adheres to critical principles designed for global scale and longevity:

1. **Universal Identifiers:** Each instance of a core entity must possess a system-generated `uuid` that serves as the immutable primary key. This is distinctly separate from the source’s native identifier (`source_id`) (e.g., Bioguide ID). This abstraction is necessary because source IDs may change or be jurisdiction-specific, whereas the global ID ensures constant, centralized entity resolution across all ingestion pipelines (e.g., whether the data originated from Congress.gov or LegiScan).
2. **Hierarchical Linkage:** The model must enforce clear and consistent parent-child relationships, specifically linking the `PublicOfficial` and `VotingRecord` entities to the `LegislativeBody` entity. This accommodates complex legislative structures, such as bicameral systems (House $\\leftrightarrow$ Senate) and multi-national groups (EU Parliament).
3. **Normalized Enumerations:** All high-cardinality categorical data, particularly the `vote_position`, must utilize a standardized set of string enumerations (e.g., `YEA`, `NAY`, `ABSENT`, `NOT_VOTING`). This normalization step is vital for unifying disparate source labels—for instance, mapping the US “Yea” or UK “Aye” to the common `YEA` value, facilitating unified analysis.

### **B. Entity Relationship Model (Conceptual)**

The CDM is structured around four interconnected entities:

* **LegislativeBody (Parent Entity):** Defines the legislative jurisdiction and chamber structure.  
* **PublicOfficial (Member Entity):** Stores all biographical, role, and tenure information. Linked to LegislativeBody.  
* **VotingRecord (Event Entity):** Contains metadata about a single legislative vote event. Linked to LegislativeBody.  
* **MemberVote (Fact/Linker Entity):** Records the specific position taken by an official on a vote. This entity links PublicOfficial and VotingRecord via foreign keys.

### **C. Entity Definition 1: LegislativeBody**

This entity captures the structural and geographical context required for global differentiation.

| Property Name | Data Type | Description | Requirement | Extensibility Note |
| :---- | :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key, uniquely identifying the chamber/body. | Mandatory |  |
| source_id | String | Source-provided identifier for the chamber/body. | Mandatory | Supports reconciliation with external data catalogs. |
| jurisdiction_type | Enum (String) | Federal (US), State (TX), Provincial, Supranational (EU). | Mandatory | Defines the governmental level. |
| jurisdiction_code | String | ISO Alpha-2 country code (US, UK) or regional ID. | Mandatory | Links the body to a political geography (e.g., US Federal, TX State). |
| name | String | Official chamber name (e.g., "U.S. House of Representatives," "House of Lords"). | Mandatory | Required to distinguish between UK Commons vs. Lords.8 |
| chamber_type | Enum (String) | Upper (Senate, Lords), Lower (House), Unicameral, Supranational. | Mandatory | Enforces normalization of bicameral systems. |
| session | String | Session or legislature identifier supplied by the source. | Optional | Supports multi-session jurisdictions (e.g., Congress number). |

### **D. Entity Definition 2: PublicOfficial**

The PublicOfficial entity is designed to store standardized biographical and role data. The US Federal data will map the bioguideId to the `source_id`.3

Table 3: Common Data Model (CDM) Entity: PublicOfficial Property Definitions

| Property Name | Data Type | Description | Requirement | Source Mapping Example (US Federal) | Extensibility Note |
| :---- | :---- | :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key. | Mandatory | Generated UUID | Required for analytical joins. |
| source_id | String | Unique source identifier. | Mandatory | bioguideId 3 | Maps to various unique IDs globally (MEP ID, MP ID). |
| legislative_body_uuid | String | Foreign key linking to the specific chamber (`LegislativeBody.uuid`). | Mandatory | ID for the "US House (118th Congress)" | Tracks specific tenure within a body. |
| full_name | String | Official's canonical name. | Mandatory | Name fields from /member endpoint 3 | Standardized format required for cross-jurisdictional querying. |
| party_affiliation | String | Current political party or group code. | Mandatory | Party code (e.g., "R", "D") 1 | Must generalize to accommodate EU "Group" IDs.2 |
| role_title | String | Specific role title (e.g., Senator, MEP, MP). | Mandatory | Title from Congress role data 1 | Retains jurisdiction-specific role terminology. |
| jurisdiction_region_code | String (Alpha-2) | State or Country code. | Mandatory | State Code from /member/{stateCode} 3 | Maps to Country (EU/UK). |
| district_identifier | String | District number, constituency name, or electoral region code. | Conditional | District number from Congress data 3 | Must accommodate numerical US districts and descriptive UK constituencies.9 |
| term_start_date | Date | Date the current term began. | Optional | Role data start date 1 | Supports historical tenure tracking. |
| term_end_date | Date | Date the current term ended or is expected to end. | Optional | Role data end date 1 | Enables vacancy forecasting and transition analysis. |
| office_status | Enum (String) | Current status of the seat (Active, Vacant, Suspended, Retired). | Mandatory | Derived from status feeds | Supports vacancy management and resignations. |
| biography_url | String | External biography reference. | Optional | Biography links from Congress.gov | Facilitates enrichment. |
| photo_url | String | Image asset URI. | Optional | Photo URL from member detail | Supports UI rendering. |

### **E. Entity Definition 3 & 4: VotingRecord and MemberVote**

These entities capture the vote event metadata and the granular individual position. The normalization of the vote\_position enumeration is the most critical element for making voting records globally comparable.

Table 4: Common Data Model (CDM) Entity: VotingRecord Property Definitions

| Property Name | Data Type | Description | Source Mapping Example (US Federal) | Extensibility Note |
| :---- | :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key for the vote event. | Generated UUID | Required for linking multiple member votes. |
| source_id | String | Unique source identifier. | Concatenation of `{congress}-{session}-{voteNumber}` 3 | Required for reconciliation with source data. |
| legislative_body_uuid | String | Foreign key linking to the legislative chamber (`LegislativeBody.uuid`). | Chamber ID (House/Senate) | Allows precise vote filtering by chamber. |
| vote_date_utc | DateTime | Date and time the roll call vote occurred (UTC standard). | Data from roll call response 1 | Timezone standardization is mandatory for international data coherence. |
| subject_summary | String | Short description of the vote matter. | Description fields from vote endpoint 1 | Essential for vote classification and topic analysis. |
| bill_reference | String | Identifier for the related bill or motion. | Bill data linked to the vote | Provides legislative context. |
| bill_uri | String | Canonical URI to the bill or motion, when provided. | Bill URLs from vote metadata | Supports deep linking and traceability. |
| roll_call_reference | String | Source-provided roll call reference number or URL. | Roll call link from Congress.gov | Enables verification workflows. |
| member_votes | Array<MemberVote> | Embedded collection of member vote facts for the record. | Member positions payload | Simplifies event transmission. |

Table 5: Common Data Model (CDM) Entity: MemberVote Property Definitions

| Property Name | Data Type | Description | Source Mapping Example (US Federal) | Extensibility Note |
| :---- | :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key for the specific member's action. | Generated UUID | Ensures unique tracking of each official’s decision. |
| source_id | String | Unique source identifier for the member vote when available. | `{voteNumber}-{bioguideId}` | Supports deduplication from high-volume feeds. |
| official_uuid | String | Foreign key linking to the `PublicOfficial` (`uuid`). | Derived official UUID | Links the position to the individual. |
| voting_record_uuid | String | Foreign key linking back to the parent `VotingRecord`. | Generated UUID | Supports fact table joins. |
| vote_position | Enum (String) | The official's recorded position. **Standardized ENUM: `YEA`, `NAY`, `ABSENT`, `NOT_VOTING`.** | Position data from /members endpoint 3 | Normalizes disparate terms (e.g., Aye, No, Present) globally. |
| group_position | String | Position dictated by the member's party or political group. | Future field, not provided by Congress.gov API but anticipated for LegiScan/EU data. | Crucial for party cohesion and compliance analysis. |
| notes | String | Free-form annotations or ingest warnings. | Ingestion service warnings | Supports audit trails and remediation. |

### **F. Support Entities: AccountabilityMetric and OfficialAccountabilityEvent**

While the initial ingestion scope prioritizes officials and vote data, the platform must also carry accountability scoring metadata and a transport envelope for Kafka streaming. These entities ensure downstream services receive a fully contextual payload.

Table 6: Common Data Model (CDM) Entity: AccountabilityMetric Property Definitions

| Property Name | Data Type | Description | Extensibility Note |
| :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key for the accountability metric. | Supports independent lifecycle management for each metric version. |
| source_id | String | Source-provided identifier (if available). | Allows reconciliation with third-party scoring services. |
| name | String | Metric name (e.g., "Promise Alignment"). | Enables multi-metric analytics without schema changes. |
| score | Double | Normalized score value. | Supports fractional scoring models. |
| methodology_version | String | Version label for the scoring methodology. | Critical for auditability when methodologies change. |
| details | String | Free-form explanation or calculation notes. | Facilitates transparency in accountability reporting. |

Table 7: Event Envelope: OfficialAccountabilityEvent Property Definitions

| Property Name | Data Type | Description | Extensibility Note |
| :---- | :---- | :---- | :---- |
| uuid | String (UUID) | Primary key for the event envelope. | Guarantees idempotent processing across services. |
| source_id | String | Source identifier for the event payload. | Supports traceability when replaying source events. |
| captured_at | DateTime | Timestamp (UTC) when the event snapshot was produced. | Enables ordering and freshness analytics. |
| ingestion_source | String | Identifier for the ingestion agent/service. | Supports multi-agent federation across jurisdictions. |
| partition_key | String | Deterministic key used for Kafka partitioning. | Ensures all updates for an official land on the same partition. |
| legislative_body | LegislativeBody | Embedded jurisdiction context for the event. | Provides downstream services with immediate hierarchy metadata. |
| public_official | PublicOfficial | Embedded official profile snapshot. | Supports stateless consumers that do not maintain a lookup cache. |
| voting_records | Array<VotingRecord> | Set of vote events included in this message. | Allows batching multiple votes per official per event. |
| accountability_metrics | Array<AccountabilityMetric> | Optional accountability scoring summaries. | Extends naturally as more metrics are introduced. |

## **V. Agentic Development Input and Data Mapping Specifications**

The success of the agentic development relies on precise, prescriptive instructions for the data mapping between the retrieved API data and the normalized CDM schema. The following specifications define the mandatory logic for the CongressGovV3Agent.

### **A. Required Mappings for US Federal Ingestion**

The ingestion agent must execute the following atomic mapping steps for every ingested record:

1. **Unique ID Generation:** For every new entity instance (`PublicOfficial`, `VotingRecord`, `MemberVote`), a system-generated UUID must be created for the `uuid` field immediately upon processing the source record. This global ID must be used as the internal primary key, independent of the source identifier.
2. **Official ID Mapping:** The Congress.gov unique identifier, the $member.bioguideId, must be mapped directly to the `source_id` property in the `PublicOfficial` entity.
3. **Vote ID Mapping:** Since Congress.gov uses composite path parameters for vote identification, the `source_id` on `VotingRecord` must be constructed via concatenation: "{congress}-{session}-{voteNumber}". This composite key ensures source traceability for the unique vote event.3
4. **Vote Position Normalization:** The source API's position codes (which may vary in capitalization or abbreviation, e.g., 'Y', 'N', 'NV') must be mapped strictly to the CDM's standardized ENUM:  
   * Source 'Y' (Yea/Aye) $\\rightarrow$ CDM YEA  
   * Source 'N' (Nay/No) $\\rightarrow$ CDM NAY  
   * Source 'P' (Present) $\\rightarrow$ CDM ABSENT (or a more granular PRESENT if required)  
   * Source 'NV' (Not Voting) $\\rightarrow$ CDM NOT\_VOTING

### **B. Future-Proofing the Model (Handling International Variances)**

The CDM is engineered to anticipate structural differences outside the US Federal context, ensuring high extensibility:

* **Political Grouping Abstraction:** The `party_affiliation` field is designed as a generalized string identifier. While it stores simple party codes (R, D) for the US Federal system 1, it will accommodate the more complex, descriptive Group names utilized in the EU Parliament data.2 This abstraction prevents the US two-party system from dictating the global model structure. Furthermore, the handling of members changing parties or roles during a term, an event noted in ProPublica's historical documentation 1, is managed by linking the `PublicOfficial` entity to the specific `legislative_body_uuid` which includes the temporal context of the Congress or Session.
* **Regional and Constituency Mapping:** The UK Parliament's APIs necessitate mapping MPs to named constituencies, sometimes including geometry and election results.9 By defining district\_identifier as a flexible string type, the CDM can seamlessly accommodate numerical US district codes 3 alongside descriptive international constituency names, maintaining a single, unified field for regional linkage.  
* **Auxiliary Data Integration:** While the immediate focus is on voting records, the Congress.gov API includes endpoints for data like sponsored and cosponsored legislation.3 These ancillary data streams should be stored in dedicated, related tables (e.g., OfficialLegislation) that link back to the PublicOfficial entity via the global UUID, preventing the core biographical entity from becoming overly complex.

### **C. Data Architecture Diagram Specification**

The underlying data architecture is a classic dimensional model, optimized for analytical performance and massive scalability, particularly crucial for global expansion. The design isolates the high-volume, repetitive vote positions into a central fact table.

The **MemberVote Fact Table** is the scalable anchor. It contains the minimal amount of data necessary: only foreign keys (`official_uuid`, `voting_record_uuid`) and the single, normalized `vote_position`. By keeping this table highly granular and narrow, the system is designed to absorb the high volume of historical data that will eventually be ingested. Analytical queries are executed by efficiently joining this lightweight fact table against the dimension tables (`PublicOfficial`, `VotingRecord`, `LegislativeBody`), which change much less frequently. This design ensures the platform can rapidly process complex analytical questions (e.g., party cohesion scores, historical voting alignment) without degradation, irrespective of the number of jurisdictions integrated.

## **VI. Conclusions and Recommendations**

The established plan provides a complete technical roadmap for implementing the US Federal ingestion pipeline and defining a globally extensible Common Data Model. The primary architecture successfully addresses the immediate limitations identified in the source data.

### **Architectural Recommendations:**

1. **Rate Limit Governance (US Federal):** The initial implementation must prioritize the development of a highly robust rate-limiting governor integrated into the CongressGovV3Agent. This mechanism must be the single point of control for API calls, ensuring the agent remains strictly below the 5,000 RPH threshold to maintain continuous delta loading capability for current votes.  
2. **Beta Endpoint Monitoring:** Given that US House voting data relies on beta endpoints 3, the ingestion agent requires continuous, automated schema monitoring. Any detected deviation must trigger an immediate alert and invoke the defined graceful error handling routine to prevent upstream processing failure.  
3. **Authentication Standardization:** All ingestion agents, including the future LegiScan and international agents, must standardize API key management via secure vaulting systems. The requirement for a Data.gov key for Congress.gov 3 establishes a precedent for official government authorization that should be replicated across other federal and state systems where possible.  
4. **Future Bulk Data Strategy:** The project should allocate resources for persistent discovery and testing of GPO's bulk data repository and the govinfo API, specifically targeting the Roll Call Votes resource.6 Transitioning historical ingestion to a bulk file transfer method, rather than relying on the rate-limited API, represents the only scalable long-term solution for full historical coverage.

#### **Works cited**

1. ProPublica Congress API, accessed October 26, 2025, [https://projects.propublica.org/api-docs/congress-api/](https://projects.propublica.org/api-docs/congress-api/)  
2. Developers · HowTheyVote.eu, accessed October 26, 2025, [https://howtheyvote.eu/developers](https://howtheyvote.eu/developers)  
3. Congress.gov API, accessed October 26, 2025, [https://gpo.congress.gov/](https://gpo.congress.gov/)  
4. Congress.gov New, Tip, and Top – March 2024 | In Custodia Legis, accessed October 26, 2025, [https://blogs.loc.gov/law/2024/03/congress-gov-new-tip-and-top-march-2024/](https://blogs.loc.gov/law/2024/03/congress-gov-new-tip-and-top-march-2024/)  
5. LibraryOfCongress/api.congress.gov \- GitHub, accessed October 26, 2025, [https://github.com/LibraryOfCongress/api.congress.gov](https://github.com/LibraryOfCongress/api.congress.gov)  
6. Using Congress.gov Data Offsite | Congress.gov | Library of Congress, accessed October 26, 2025, [https://www.congress.gov/help/using-data-offsite](https://www.congress.gov/help/using-data-offsite)  
7. LegiScan API | LegiScan, accessed October 26, 2025, [https://legiscan.com/legiscan](https://legiscan.com/legiscan)  
8. Developer hub \- UK Parliament, accessed October 26, 2025, [https://developer.parliament.uk/](https://developer.parliament.uk/)  
9. Members API \- UK Parliament, accessed October 26, 2025, [https://members-api.parliament.uk/index.html](https://members-api.parliament.uk/index.html)  
10. Congress.gov API | Additional APIs and Data Services | APIs at the ..., accessed October 26, 2025, [https://www.loc.gov/apis/additional-apis/congress-dot-gov-api/](https://www.loc.gov/apis/additional-apis/congress-dot-gov-api/)  
11. About \- HowTheyVote.eu, accessed October 26, 2025, [https://howtheyvote.eu/about](https://howtheyvote.eu/about)  
12. LegiScan | Bringing People to the Process, accessed October 26, 2025, [https://legiscan.com/](https://legiscan.com/)