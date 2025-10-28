package com.beacon.congress.client;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.JurisdictionType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal Congress.gov API client that can list legislative bodies, enumerate members, and fetch
 * detailed member records while mapping the responses into our protobuf domain models.
 */
public final class CongressGovClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CongressGovClient.class);
    private static final Map<String, String> STATE_ABBREVIATIONS = Map.ofEntries(
            Map.entry("alabama", "AL"),
            Map.entry("alaska", "AK"),
            Map.entry("arizona", "AZ"),
            Map.entry("arkansas", "AR"),
            Map.entry("california", "CA"),
            Map.entry("colorado", "CO"),
            Map.entry("connecticut", "CT"),
            Map.entry("delaware", "DE"),
            Map.entry("district of columbia", "DC"),
            Map.entry("florida", "FL"),
            Map.entry("georgia", "GA"),
            Map.entry("hawaii", "HI"),
            Map.entry("idaho", "ID"),
            Map.entry("illinois", "IL"),
            Map.entry("indiana", "IN"),
            Map.entry("iowa", "IA"),
            Map.entry("kansas", "KS"),
            Map.entry("kentucky", "KY"),
            Map.entry("louisiana", "LA"),
            Map.entry("maine", "ME"),
            Map.entry("maryland", "MD"),
            Map.entry("massachusetts", "MA"),
            Map.entry("michigan", "MI"),
            Map.entry("minnesota", "MN"),
            Map.entry("mississippi", "MS"),
            Map.entry("missouri", "MO"),
            Map.entry("montana", "MT"),
            Map.entry("nebraska", "NE"),
            Map.entry("nevada", "NV"),
            Map.entry("new hampshire", "NH"),
            Map.entry("new jersey", "NJ"),
            Map.entry("new mexico", "NM"),
            Map.entry("new york", "NY"),
            Map.entry("north carolina", "NC"),
            Map.entry("north dakota", "ND"),
            Map.entry("ohio", "OH"),
            Map.entry("oklahoma", "OK"),
            Map.entry("oregon", "OR"),
            Map.entry("pennsylvania", "PA"),
            Map.entry("rhode island", "RI"),
            Map.entry("south carolina", "SC"),
            Map.entry("south dakota", "SD"),
            Map.entry("tennessee", "TN"),
            Map.entry("texas", "TX"),
            Map.entry("utah", "UT"),
            Map.entry("vermont", "VT"),
            Map.entry("virginia", "VA"),
            Map.entry("washington", "WA"),
            Map.entry("west virginia", "WV"),
            Map.entry("wisconsin", "WI"),
            Map.entry("wyoming", "WY"),
            Map.entry("puerto rico", "PR"),
            Map.entry("guam", "GU"),
            Map.entry("american samoa", "AS"),
            Map.entry("northern mariana islands", "MP"),
            Map.entry("virgin islands", "VI")
    );

    private final CongressGovClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Map<Integer, List<MemberRecord>> memberCache = new ConcurrentHashMap<>();
    private final Map<Integer, Map<ChamberType, LegislativeBody>> bodyCache = new ConcurrentHashMap<>();

    public CongressGovClient(CongressGovClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.requestTimeout())
                .build();
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /** Returns the list of legislative bodies (House/Senate) for the supplied Congress number. */
    public List<LegislativeBody> fetchLegislativeBodies(int congressNumber) {
        return new ArrayList<>(getLegislativeBodyMap(congressNumber).values());
    }

    /**
     * Returns members for the supplied chamber. When {@code chamberFilter} is {@code null} the
     * entire delegation is returned.
     */
    public List<PublicOfficial> fetchMembers(int congressNumber, ChamberType chamberFilter) {
        Map<ChamberType, LegislativeBody> bodies = getLegislativeBodyMap(congressNumber);
        return getMembersForCongress(congressNumber).stream()
                .filter(record -> chamberFilter == null || chamberFilter == ChamberType.CHAMBER_TYPE_UNSPECIFIED || record.chamberType() == chamberFilter)
                .map(record -> toPublicOfficial(record, bodies.get(record.chamberType())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<MemberListing> fetchMemberListings(int congressNumber, ChamberType chamberFilter) {
        Map<ChamberType, LegislativeBody> bodies = getLegislativeBodyMap(congressNumber);
        return getMembersForCongress(congressNumber).stream()
                .filter(record -> chamberFilter == null || chamberFilter == ChamberType.CHAMBER_TYPE_UNSPECIFIED || record.chamberType() == chamberFilter)
                .map(record -> {
                    LegislativeBody body = bodies.get(record.chamberType());
                    PublicOfficial official = toPublicOfficial(record, body);
                    if (official == null) {
                        return null;
                    }
                    return new MemberListing(official, body, record.rawJson());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Fetches a detailed record for the provided member id. The same conversion logic used by the
     * ingestion service is applied so that integration tests validate the exact mapping.
     */
    public Optional<PublicOfficial> fetchMemberDetails(String bioguideId, int congressNumber) {
        if (bioguideId == null || bioguideId.isBlank()) {
            return Optional.empty();
        }
        URI uri = buildUri("/member/" + bioguideId, Map.of());
        JsonNode root = fetchJson(uri);
        JsonNode memberNode = root.path("member");
        if (memberNode.isMissingNode()) {
            return Optional.empty();
        }
        return toMemberRecord(memberNode)
                .map(record -> {
                    Map<ChamberType, LegislativeBody> bodies = getLegislativeBodyMap(congressNumber);
                    return toPublicOfficial(record, bodies.get(record.chamberType()));
                });
    }

    private Map<ChamberType, LegislativeBody> getLegislativeBodyMap(int congressNumber) {
        return bodyCache.computeIfAbsent(congressNumber, key -> {
            Map<ChamberType, LegislativeBody> map = new EnumMap<>(ChamberType.class);
            for (MemberRecord record : getMembersForCongress(congressNumber)) {
                map.computeIfAbsent(record.chamberType(), chamber -> buildLegislativeBody(congressNumber, chamber));
                if (map.size() >= 2) {
                    break;
                }
            }
            return map;
        });
    }

    private LegislativeBody buildLegislativeBody(int congressNumber, ChamberType chamberType) {
        if (chamberType == ChamberType.CHAMBER_TYPE_UNSPECIFIED) {
            throw new CongressGovClientException("Cannot build legislative body for unspecified chamber");
        }
        String chamberName = chamberType == ChamberType.UPPER ? "U.S. Senate" : "U.S. House of Representatives";
        String sourceId = "US-%s-%d".formatted(chamberType == ChamberType.UPPER ? "SENATE" : "HOUSE", congressNumber);
        String uuid = deterministicUuid("legislative-body-" + sourceId);
        return LegislativeBody.newBuilder()
                .setUuid(uuid)
                .setSourceId(sourceId)
                .setJurisdictionCode("US")
                .setJurisdictionType(JurisdictionType.FEDERAL)
                .setName(chamberName)
                .setChamberType(chamberType)
                .setSession(String.valueOf(congressNumber))
                .build();
    }

    private List<MemberRecord> getMembersForCongress(int congressNumber) {
        return memberCache.computeIfAbsent(congressNumber, this::fetchMembersFromApi);
    }

    private List<MemberRecord> fetchMembersFromApi(int congressNumber) {
        List<MemberRecord> records = new ArrayList<>();
        URI next = buildUri("/member/congress/" + congressNumber, Map.of(
                "limit", "250",
                "currentMember", "true"
        ));
        while (next != null) {
            JsonNode root = fetchJson(next);
            JsonNode membersNode = root.path("members");
            if (membersNode.isArray()) {
                for (JsonNode memberNode : membersNode) {
                    toMemberRecord(memberNode).ifPresent(records::add);
                }
            }
            next = nextPage(root.path("pagination"));
        }
        LOGGER.info("Fetched {} members for congress {}", records.size(), congressNumber);
        return records;
    }

    private URI nextPage(JsonNode paginationNode) {
        if (paginationNode == null || paginationNode.isMissingNode()) {
            return null;
        }
        String nextLink = text(paginationNode, "next");
        if (nextLink == null || nextLink.isBlank()) {
            return null;
        }
        if (nextLink.startsWith("http")) {
            return ensureApiKey(URI.create(nextLink));
        }
        return buildUri(nextLink, Map.of());
    }

    private URI ensureApiKey(URI uri) {
        if (uri.getQuery() != null && uri.getQuery().contains("api_key=")) {
            return uri;
        }
        String connector = uri.getQuery() == null ? "?" : "&";
        return URI.create(uri.toString() + connector + "api_key=" + encode(config.apiKey()));
    }

    private Optional<MemberRecord> toMemberRecord(JsonNode memberNode) {
        String bioguideId = text(memberNode, "bioguideId");
        if (bioguideId == null || bioguideId.isBlank()) {
            return Optional.empty();
        }
        String name = firstNonBlank(
                text(memberNode, "name"),
                text(memberNode, "invertedOrderName"),
                text(memberNode, "directOrderName"),
                fallbackName(memberNode)
        );
        String party = text(memberNode, "partyName");
        String state = text(memberNode, "state");
        String district = text(memberNode, "district");
        String detailUrl = text(memberNode, "url");
        String photo = memberNode.path("depiction").path("imageUrl").asText("");
        JsonNode termsNode = memberNode.path("terms");
        ChamberType chamberType = resolveChamber(termsNode);
        if (chamberType == ChamberType.CHAMBER_TYPE_UNSPECIFIED) {
            return Optional.empty();
        }
        Instant termStart = extractTermStart(termsNode);
        String stateCode = findStateCode(termsNode, state);
        MemberRecord record = new MemberRecord(
                bioguideId,
                name,
                party,
                state,
                district == null ? "" : district,
                photo,
                detailUrl,
                chamberType,
                termStart,
                stateCode,
                memberNode.toString()
        );
        return Optional.of(record);
    }

    private ChamberType resolveChamber(JsonNode termsNode) {
        for (JsonNode term : termNodes(termsNode)) {
            String chamber = text(term, "chamber");
            ChamberType type = chamberFromString(chamber);
            if (type != ChamberType.CHAMBER_TYPE_UNSPECIFIED) {
                return type;
            }
        }
        return ChamberType.CHAMBER_TYPE_UNSPECIFIED;
    }

    private ChamberType chamberFromString(String value) {
        if (value == null) {
            return ChamberType.CHAMBER_TYPE_UNSPECIFIED;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("house")) {
            return ChamberType.LOWER;
        }
        if (normalized.contains("senate")) {
            return ChamberType.UPPER;
        }
        return ChamberType.CHAMBER_TYPE_UNSPECIFIED;
    }

    private Instant extractTermStart(JsonNode termsNode) {
        for (JsonNode term : termNodes(termsNode)) {
            Instant parsed = parseTermStart(term);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Instant parseTermStart(JsonNode term) {
        if (term == null || term.isMissingNode()) {
            return null;
        }
        String explicitStart = text(term, "start");
        if (explicitStart != null) {
            try {
                return Instant.parse(explicitStart);
            } catch (DateTimeParseException ignored) {
                // fall back to year-based parsing
            }
        }
        int startYear = term.path("startYear").asInt(0);
        if (startYear > 0) {
            return LocalDate.of(startYear, 1, 3).atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
        }
        return null;
    }

    private PublicOfficial toPublicOfficial(MemberRecord record, LegislativeBody legislativeBody) {
        if (legislativeBody == null) {
            return null;
        }
        String regionCode = Optional.ofNullable(record.stateCode())
                .filter(code -> !code.isBlank())
                .orElseGet(() -> Optional.ofNullable(record.state())
                        .map(CongressGovClient::normalizeStateCode)
                        .orElse(""));
        PublicOfficial.Builder builder = PublicOfficial.newBuilder()
                .setUuid(deterministicUuid("public-official-" + record.bioguideId()))
                .setSourceId(record.bioguideId())
                .setLegislativeBodyUuid(legislativeBody.getUuid())
                .setFullName(Optional.ofNullable(record.name()).orElse(""))
                .setPartyAffiliation(Optional.ofNullable(record.party()).orElse(""))
                .setRoleTitle(record.chamberType() == ChamberType.UPPER ? "Senator" : "Representative")
                .setJurisdictionRegionCode(regionCode)
                .setDistrictIdentifier(Optional.ofNullable(record.district()).orElse(""))
                .setOfficeStatus(OfficeStatus.ACTIVE)
                .setBiographyUrl(Optional.ofNullable(record.detailUrl()).orElse(""))
                .setPhotoUrl(Optional.ofNullable(record.photoUrl()).orElse(""));
        if (record.termStart() != null) {
            builder.setTermStartDate(toTimestamp(record.termStart()));
        }
        return builder.build();
    }

    private JsonNode fetchJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .build();
        try {
            Instant start = Instant.now();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedMillis = Duration.between(start, Instant.now()).toMillis();
            if (elapsedMillis > 1000) {
                LOGGER.info("Slow Congress.gov request: {} {} ms", uri.getPath(), elapsedMillis);
            }
            if (response.statusCode() >= 400) {
                throw new CongressGovClientException(
                        "Congress.gov request failed with status %d for %s".formatted(response.statusCode(), uri));
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new CongressGovClientException("Unable to parse Congress.gov response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CongressGovClientException("Congress.gov request interrupted", e);
        }
    }

    private URI buildUri(String path, Map<String, String> queryParams) {
        String base = path.startsWith("http") ? path : config.baseUrl() + (path.startsWith("/") ? path : "/" + path);
        String[] baseParts = base.split("\\?", 2);
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (baseParts.length == 2) {
            params.putAll(splitQuery(baseParts[1]));
        }
        params.putIfAbsent("format", "json");
        params.put("api_key", config.apiKey());
        queryParams.forEach((key, value) -> {
            if (value != null) {
                params.put(key, value);
            }
        });
        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return URI.create(baseParts[0] + "?" + query);
    }

    private Map<String, String> splitQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
    }

    private String fallbackName(JsonNode node) {
        String first = text(node, "firstName");
        String last = text(node, "lastName");
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (first != null && !first.isBlank()) {
            builder.append(first.trim());
        }
        if (last != null && !last.isBlank()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(last.trim());
        }
        return builder.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    private List<JsonNode> termNodes(JsonNode termsNode) {
        if (termsNode == null || termsNode.isMissingNode() || termsNode.isNull()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (termsNode.isArray()) {
            termsNode.forEach(nodes::add);
            return nodes;
        }
        JsonNode itemNode = termsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            nodes.add(termsNode);
            return nodes;
        }
        if (itemNode.isArray()) {
            itemNode.forEach(nodes::add);
        } else {
            nodes.add(itemNode);
        }
        return nodes;
    }

    private String findStateCode(JsonNode termsNode, String fallbackState) {
        for (JsonNode term : termNodes(termsNode)) {
            String code = text(term, "stateCode");
            if (code != null && !code.isBlank()) {
                return code.toUpperCase(Locale.ROOT);
            }
            String stateName = text(term, "stateName");
            String normalized = normalizeStateCode(stateName);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return normalizeStateCode(fallbackState);
    }

    private static String normalizeStateCode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() == 2) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return STATE_ABBREVIATIONS.getOrDefault(trimmed.toLowerCase(Locale.ROOT), "");
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static com.google.protobuf.Timestamp toTimestamp(Instant instant) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public record MemberListing(
            PublicOfficial publicOfficial,
            LegislativeBody legislativeBody,
            String sourceJson) {}

    private record MemberRecord(
            String bioguideId,
            String name,
            String party,
            String state,
            String district,
            String photoUrl,
            String detailUrl,
            ChamberType chamberType,
            Instant termStart,
            String stateCode,
            String rawJson) {}
}
