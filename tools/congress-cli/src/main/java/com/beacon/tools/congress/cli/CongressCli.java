package com.beacon.tools.congress.cli;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientConfig;
import com.beacon.congress.client.CongressGovClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Command-line interface for interacting with the Congress.gov API using the shared client.
 *
 * <p>The CLI mirrors the ingestion pipeline's behavior, so it is ideal for troubleshooting and
 * validating live responses. Command definitions follow the official API described in the
 * {@code third_party/congress.gov} reference files.</p>
 */
public final class CongressCli {

    private static final ObjectWriter JSON_WRITER;

    static {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JSON_WRITER = mapper.writer();
    }

    private final PrintStream out;
    private final PrintStream err;

    public CongressCli() {
        this(System.out, System.err);
    }

    CongressCli(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    public static void main(String[] args) {
        int exitCode = new CongressCli().execute(args);
        System.exit(exitCode);
    }

    int execute(String[] args) {
        Options options = buildOptions();
        if (args.length == 0 || hasHelpFlag(args)) {
            printUsage(options);
            return 0;
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            err.printf("Error: %s%n%n", ex.getMessage());
            printUsage(options);
            return 1;
        }

        Operation operation;
        try {
            operation = Operation.from(commandLine.getOptionValue("operation"));
        } catch (IllegalArgumentException ex) {
            err.printf("Error: %s%n%n", ex.getMessage());
            printUsage(options);
            return 1;
        }

        OutputFormat format;
        try {
            format = OutputFormat.from(commandLine.getOptionValue("format", "pretty"));
        } catch (IllegalArgumentException ex) {
            err.printf("Error: %s%n%n", ex.getMessage());
            printUsage(options);
            return 1;
        }

        int congressNumber;
        try {
            congressNumber = Integer.parseInt(commandLine.getOptionValue("congress", "118"));
        } catch (NumberFormatException ex) {
            err.println("Error: congress must be an integer value.");
            return 1;
        }

        String propertiesPath = commandLine.getOptionValue("key-file", "gradle.properties");

        Properties properties;
        try {
            properties = loadProperties(propertiesPath);
        } catch (IOException ex) {
            err.printf("Error: unable to read properties file %s (%s)%n", propertiesPath, ex.getMessage());
            return 1;
        }

        String apiKey = resolveApiKey(properties);
        if (apiKey == null || apiKey.isBlank()) {
            err.println("Error: CONGRESS_API_KEY is required. Provide it via the properties file or environment.");
            return 1;
        }

        String baseUrl = properties.getProperty("API_CONGRESS_GOV_BASE_URL");
        Duration timeout = resolveTimeout(properties.getProperty("API_CONGRESS_GOV_TIMEOUT_SECONDS"));

        CongressGovClientConfig.Builder configBuilder = CongressGovClientConfig.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            configBuilder.baseUrl(baseUrl);
        }
        if (timeout != null) {
            configBuilder.requestTimeout(timeout);
        }

        CongressGovClient client;
        try {
            client = new CongressGovClient(configBuilder.build());
        } catch (IllegalStateException ex) {
            err.printf("Error: %s%n", ex.getMessage());
            return 1;
        }

        boolean showUrl = commandLine.hasOption("show-url");

        try {
            return switch (operation) {
                case LIST_CHAMBERS -> listChambers(client, congressNumber, format, showUrl);
                case LIST_MEMBERS -> listMembers(client, commandLine, congressNumber, format, showUrl);
                case MEMBER_DETAILS -> showMemberDetails(client, commandLine, congressNumber, format, showUrl);
                case LIST_CONGRESSES -> listHouseVoteCongresses(client, format, showUrl);
                case LIST_VOTES -> listHouseVotes(client, commandLine, congressNumber, format, showUrl);
                case VOTE_MEMBERS -> listHouseVoteMembers(client, commandLine, congressNumber, format, showUrl);
            };
        } catch (CongressGovClientException ex) {
            err.printf("Congress.gov API error: %s%n", ex.getMessage());
            return 2;
        } catch (IOException ex) {
            err.printf("Error producing output: %s%n", ex.getMessage());
            return 3;
        }
    }

    private Properties loadProperties(String propertiesPath) throws IOException {
        Path path = Paths.get(propertiesPath);
        if (!Files.exists(path)) {
            throw new IOException("file does not exist");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private String resolveApiKey(Properties properties) {
        String fromEnv = System.getenv("CONGRESS_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return Optional.ofNullable(properties.getProperty("CONGRESS_API_KEY"))
                .map(String::trim)
                .orElse(null);
    }

    private Duration resolveTimeout(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(rawValue.trim());
            if (seconds <= 0) {
                throw new NumberFormatException("timeout must be positive");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ex) {
            err.printf("Warning: ignoring invalid timeout value '%s'.%n", rawValue);
            return null;
        }
    }

    private int listChambers(CongressGovClient client, int congressNumber, OutputFormat format, boolean showUrl)
            throws IOException {
        List<LegislativeBody> bodies = client.fetchLegislativeBodies(congressNumber);
        if (bodies.isEmpty()) {
            out.println("No legislative bodies returned by Congress.gov.");
            maybePrintRequestUrl(client, showUrl);
            return 0;
        }
        bodies.sort(Comparator.comparing(LegislativeBody::getChamberType));
        if (format == OutputFormat.JSON) {
            printJsonList(bodies);
        } else {
            TableRenderer renderer = new TableRenderer(List.of("UUID", "Source ID", "Name", "Chamber", "Session"));
            for (LegislativeBody body : bodies) {
                renderer.addRow(List.of(
                        body.getUuid(),
                        body.getSourceId(),
                        body.getName(),
                        body.getChamberType().name(),
                        body.getSession()
                ));
            }
            out.println(renderer.render());
            out.printf("Total records: %d%n", bodies.size());
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private int listMembers(CongressGovClient client, CommandLine commandLine, int congressNumber, OutputFormat format, boolean showUrl)
            throws IOException {
        String chamberInput = commandLine.getOptionValue("chamber");
        if (chamberInput == null || chamberInput.isBlank()) {
            err.println("Error: --chamber is required when listing members.");
            return 1;
        }
        ChamberType chamberType;
        try {
            chamberType = parseChamber(chamberInput);
        } catch (IllegalArgumentException ex) {
            err.printf("Error: %s%n", ex.getMessage());
            return 1;
        }

        Boolean currentMember = parseCurrentFlag(commandLine.getOptionValue("current"));
        Integer lastYears = parseLastYears(commandLine.getOptionValue("last-years"));
        if (lastYears != null && lastYears <= 0) {
            err.println("Error: --last-years must be a positive integer.");
            return 1;
        }

        String startDate = null;
        String endDate = null;
        ZonedDateTime cutoffDateTime = null;
        if (lastYears != null) {
            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
            cutoffDateTime = nowUtc.minusYears(lastYears);
            startDate = cutoffDateTime.toLocalDate().toString();
            endDate = nowUtc.toLocalDate().toString();
        }

        List<PublicOfficial> officials = client.fetchMembers(congressNumber, chamberType, currentMember, startDate, endDate);
        if (officials.isEmpty()) {
            out.println("No members returned for the requested chamber.");
            maybePrintRequestUrl(client, showUrl);
            return 0;
        }
        if (lastYears != null) {
            Instant cutoff = cutoffDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            officials = officials.stream()
                    .filter(official -> isWithinLastYears(official, cutoff))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (officials.isEmpty()) {
                out.println("No members matched the requested filters.");
                maybePrintRequestUrl(client, showUrl);
                return 0;
            }
        }
        officials.sort(Comparator.comparing(PublicOfficial::getFullName));
        if (format == OutputFormat.JSON) {
            printJsonList(officials);
        } else {
            TableRenderer renderer = new TableRenderer(List.of(
                    "Source ID",
                    "Full Name",
                    "Party",
                    "State",
                    "District",
                    "Status"
            ));
            for (PublicOfficial official : officials) {
                renderer.addRow(List.of(
                        emptySafe(official.getSourceId()),
                        emptySafe(official.getFullName()),
                        emptySafe(official.getPartyAffiliation()),
                        emptySafe(official.getJurisdictionRegionCode()),
                        emptySafe(official.getDistrictIdentifier()),
                        official.getOfficeStatus().name()
                ));
            }
            out.println(renderer.render());
            out.printf("Total records: %d%n", officials.size());
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private int showMemberDetails(CongressGovClient client, CommandLine commandLine, int congressNumber,
            OutputFormat format, boolean showUrl) throws IOException {
        String memberId = commandLine.getOptionValue("memberId");
        if (memberId == null || memberId.isBlank()) {
            err.println("Error: --memberId is required for member-details.");
            return 1;
        }
        Optional<PublicOfficial> official = client.fetchMemberDetails(memberId.trim(), congressNumber);
        if (official.isEmpty()) {
            err.printf("No member found for id %s.%n", memberId);
            return 1;
        }
        if (format == OutputFormat.JSON) {
            printJson(official.get());
        } else {
            PublicOfficial record = official.get();
            TableRenderer renderer = new TableRenderer(List.of("Field", "Value"));
            renderer.addRow(List.of("Source ID", emptySafe(record.getSourceId())));
            renderer.addRow(List.of("Full Name", emptySafe(record.getFullName())));
            renderer.addRow(List.of("Party", emptySafe(record.getPartyAffiliation())));
            renderer.addRow(List.of("Chamber UUID", emptySafe(record.getLegislativeBodyUuid())));
            renderer.addRow(List.of("Role", emptySafe(record.getRoleTitle())));
            renderer.addRow(List.of("State", emptySafe(record.getJurisdictionRegionCode())));
            renderer.addRow(List.of("District", emptySafe(record.getDistrictIdentifier())));
            renderer.addRow(List.of("Office Status", record.getOfficeStatus().name()));
            renderer.addRow(List.of("Biography URL", emptySafe(record.getBiographyUrl())));
            renderer.addRow(List.of("Photo URL", emptySafe(record.getPhotoUrl())));
            renderer.addRow(List.of("Term Start", TimestampFormatter.format(record.getTermStartDate().getSeconds())));
            renderer.addRow(List.of("Term End", TimestampFormatter.format(record.getTermEndDate().getSeconds())));
            out.println(renderer.render());
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private int listHouseVoteCongresses(CongressGovClient client, OutputFormat format, boolean showUrl) throws IOException {
        List<Integer> congresses = client.fetchAvailableHouseVoteCongresses();
        if (congresses.isEmpty()) {
            out.println("No congress numbers returned for House votes.");
            maybePrintRequestUrl(client, showUrl);
            return 0;
        }
        congresses.sort(Comparator.reverseOrder());
        if (format == OutputFormat.JSON) {
            printJsonForIntegers(congresses);
        } else {
            TableRenderer renderer = new TableRenderer(List.of("Congress Number"));
            for (Integer congress : congresses) {
                renderer.addRow(List.of(String.valueOf(congress)));
            }
            out.println(renderer.render());
            out.printf("Total congresses: %d%n", congresses.size());
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private int listHouseVotes(
            CongressGovClient client,
            CommandLine commandLine,
            int congressNumber,
            OutputFormat format,
            boolean showUrl) throws IOException {
        String chamberInput = commandLine.getOptionValue("chamber", "house");
        ChamberType chamberType;
        try {
            chamberType = parseChamber(chamberInput);
        } catch (IllegalArgumentException ex) {
            err.printf("Error: %s%n", ex.getMessage());
            return 1;
        }
        if (chamberType != ChamberType.LOWER) {
            err.println("Error: vote listings currently support the House of Representatives only.");
            return 1;
        }

        String sessionRaw = commandLine.getOptionValue("session");
        Integer requestedSession = null;
        if (sessionRaw != null && !sessionRaw.isBlank()) {
            try {
                requestedSession = Integer.parseInt(sessionRaw.trim());
            } catch (NumberFormatException ex) {
                err.printf("Error: invalid session value '%s'. Expected 1 or 2.%n", sessionRaw);
                return 1;
            }
            if (requestedSession != 1 && requestedSession != 2) {
                err.printf("Error: invalid session value '%s'. Expected 1 or 2.%n", sessionRaw);
                return 1;
            }
        }

        List<CongressGovClient.HouseVoteSummary> summaries = new ArrayList<>();
        if (requestedSession != null) {
            summaries.addAll(client.fetchHouseVoteSummaries(congressNumber, requestedSession));
        } else {
            for (int session = 1; session <= 2; session++) {
                summaries.addAll(client.fetchHouseVoteSummaries(congressNumber, session));
            }
        }
        if (summaries.isEmpty()) {
            out.printf("No House votes returned for congress %d.%n", congressNumber);
            maybePrintRequestUrl(client, showUrl);
            return 0;
        }
        summaries.sort(Comparator
                .comparingInt(CongressGovClient.HouseVoteSummary::sessionNumber)
                .thenComparingInt(CongressGovClient.HouseVoteSummary::rollCallNumber));

        if (format == OutputFormat.JSON) {
            printJsonForVotes(summaries, congressNumber);
        } else {
            TableRenderer renderer = new TableRenderer(List.of(
                    "Session",
                    "Roll Call",
                    "Result",
                    "Vote Type",
                    "Legislation",
                    "Start (UTC)",
                    "Updated (UTC)"
            ));
            for (CongressGovClient.HouseVoteSummary summary : summaries) {
                renderer.addRow(List.of(
                        String.valueOf(summary.sessionNumber()),
                        String.valueOf(summary.rollCallNumber()),
                        emptySafe(summary.result()),
                        emptySafe(summary.voteType()),
                        emptySafe(buildLegislationLabel(summary.legislationType(), summary.legislationNumber())),
                        formatInstant(summary.startDate()),
                        formatInstant(summary.updateDate())
                ));
            }
            out.println(renderer.render());
            out.printf("Total records: %d%n", summaries.size());
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private int listHouseVoteMembers(
            CongressGovClient client,
            CommandLine commandLine,
            int congressNumber,
            OutputFormat format,
            boolean showUrl) throws IOException {
        String chamberInput = commandLine.getOptionValue("chamber", "house");
        ChamberType chamberType;
        try {
            chamberType = parseChamber(chamberInput);
        } catch (IllegalArgumentException ex) {
            err.printf("Error: %s%n", ex.getMessage());
            return 1;
        }
        if (chamberType != ChamberType.LOWER) {
            err.println("Error: vote member listings currently support the House of Representatives only.");
            return 1;
        }

        String sessionRaw = commandLine.getOptionValue("session");
        if (sessionRaw == null || sessionRaw.isBlank()) {
            err.println("Error: --session is required for vote-members.");
            return 1;
        }
        int sessionNumber;
        try {
            sessionNumber = Integer.parseInt(sessionRaw.trim());
        } catch (NumberFormatException ex) {
            err.printf("Error: invalid session value '%s'. Expected 1 or 2.%n", sessionRaw);
            return 1;
        }
        if (sessionNumber != 1 && sessionNumber != 2) {
            err.printf("Error: invalid session value '%s'. Expected 1 or 2.%n", sessionRaw);
            return 1;
        }

        String voteRaw = commandLine.getOptionValue("vote-number");
        if (voteRaw == null || voteRaw.isBlank()) {
            err.println("Error: --vote-number is required for vote-members.");
            return 1;
        }
        int voteNumber;
        try {
            voteNumber = Integer.parseInt(voteRaw.trim());
        } catch (NumberFormatException ex) {
            err.printf("Error: invalid vote number '%s'.%n", voteRaw);
            return 1;
        }

        CongressGovClient.HouseVoteDetail detail = client.fetchHouseVoteDetail(congressNumber, sessionNumber, voteNumber);
        Map<String, CongressGovClient.MemberVoteResult> memberVotes = detail.memberVotes();
        if (memberVotes.isEmpty()) {
            out.printf("No member votes returned for congress %d session %d roll call %d.%n", congressNumber, sessionNumber, voteNumber);
            maybePrintRequestUrl(client, showUrl);
            return 0;
        }

        if (format == OutputFormat.JSON) {
            printJsonForVoteMembers(detail);
        } else {
            out.printf("Congress: %d  Session: %d  Roll Call: %d%n", detail.congressNumber(), detail.sessionNumber(), detail.rollCallNumber());
            out.printf("Question: %s%n", emptySafe(detail.question()));
            out.printf("Result: %s%n", emptySafe(detail.result()));
            out.printf("Vote Type: %s%n", emptySafe(detail.voteType()));
            out.printf("Legislation: %s%n", emptySafe(buildLegislationLabel(detail.legislationType(), detail.legislationNumber())));
            out.printf("Legislation URL: %s%n", emptySafe(detail.legislationUrl()));
            out.printf("Source URL: %s%n", emptySafe(detail.sourceDataUrl()));
            out.println();

            TableRenderer renderer = new TableRenderer(List.of("Bioguide ID", "Vote Cast"));
            memberVotes.forEach((bioguideId, voteResult) ->
                    renderer.addRow(List.of(
                            emptySafe(bioguideId),
                            emptySafe(voteResult.voteCast())
                    )));
            out.println(renderer.render());
            out.printf("Total members: %d%n", memberVotes.size());

            Map<String, Long> distribution = memberVotes.values().stream()
                    .map(CongressGovClient.MemberVoteResult::voteCast)
                    .map(value -> value == null || value.isBlank() ? "UNSPECIFIED" : value)
                    .collect(Collectors.groupingBy(
                            value -> value,
                            LinkedHashMap::new,
                            Collectors.counting()));
            if (!distribution.isEmpty()) {
                out.println("Vote distribution:");
                distribution.forEach((label, count) -> out.printf("  %s: %d%n", label, count));
            }
        }
        maybePrintRequestUrl(client, showUrl);
        return 0;
    }

    private ChamberType parseChamber(String value) {
        String normalized = value.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "LOWER", "HOUSE", "H", "HOR" -> ChamberType.LOWER;
            case "UPPER", "SENATE", "S" -> ChamberType.UPPER;
            default -> throw new IllegalArgumentException(
                    "Unknown chamber '" + value + "'. Expected HOUSE/LOWER or SENATE/UPPER.");
        };
    }

    private String emptySafe(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private boolean isWithinLastYears(PublicOfficial official, Instant cutoff) {
        if (!official.hasTermStartDate()) {
            return false;
        }
        Instant start = toInstant(official.getTermStartDate());
        return !start.isBefore(cutoff);
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private Boolean parseCurrentFlag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        err.printf("Warning: ignoring invalid --current value '%s'.%n", value);
        return null;
    }

    private Integer parseLastYears(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            err.printf("Warning: ignoring invalid --last-years value '%s'.%n", value);
            return null;
        }
    }

    private void maybePrintRequestUrl(CongressGovClient client, boolean showUrl) {
        if (!showUrl) {
            return;
        }
        client.getLastRequestUri().ifPresent(uri -> out.printf("Request URL: %s%n", uri));
    }

    private void printJsonForIntegers(List<Integer> values) {
        printAsJson(values);
    }

    private void printJsonForVotes(List<CongressGovClient.HouseVoteSummary> summaries, int congressNumber) {
        List<VoteSummaryJson> payload = summaries.stream()
                .map(summary -> new VoteSummaryJson(
                        congressNumber,
                        summary.sessionNumber(),
                        summary.rollCallNumber(),
                        summary.result(),
                        summary.voteType(),
                        summary.legislationType(),
                        summary.legislationNumber(),
                        summary.legislationUrl(),
                        summary.sourceDataUrl(),
                        summary.startDate(),
                        summary.updateDate()))
                .collect(Collectors.toList());
        printAsJson(payload);
    }

    private void printJsonForVoteMembers(CongressGovClient.HouseVoteDetail detail) {
        List<VoteMemberJson> members = detail.memberVotes().entrySet().stream()
                .map(entry -> new VoteMemberJson(entry.getKey(), entry.getValue().voteCast()))
                .collect(Collectors.toList());
        VoteDetailJson payload = new VoteDetailJson(
                detail.congressNumber(),
                detail.sessionNumber(),
                detail.rollCallNumber(),
                detail.question(),
                detail.result(),
                detail.voteType(),
                detail.legislationType(),
                detail.legislationNumber(),
                detail.legislationUrl(),
                detail.sourceDataUrl(),
                detail.startDate(),
                detail.updateDate(),
                members);
        printAsJson(payload);
    }

    private void printAsJson(Object value) {
        try {
            out.println(JSON_WRITER.writeValueAsString(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to encode JSON output", ex);
        }
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : instant.toString();
    }

    private String buildLegislationLabel(String type, String number) {
        String normalizedType = type == null ? "" : type.trim();
        String normalizedNumber = number == null ? "" : number.trim();
        if (normalizedType.isEmpty() && normalizedNumber.isEmpty()) {
            return "-";
        }
        if (normalizedType.isEmpty()) {
            return normalizedNumber;
        }
        if (normalizedNumber.isEmpty()) {
            return normalizedType;
        }
        return normalizedType + " " + normalizedNumber;
    }

    private record VoteSummaryJson(
            int congress,
            int sessionNumber,
            int rollCallNumber,
            String result,
            String voteType,
            String legislationType,
            String legislationNumber,
            String legislationUrl,
            String sourceDataUrl,
            Instant startDateUtc,
            Instant updateDateUtc) {}

    private record VoteMemberJson(String bioguideId, String voteCast) {}

    private record VoteDetailJson(
            int congress,
            int sessionNumber,
            int rollCallNumber,
            String question,
            String result,
            String voteType,
            String legislationType,
            String legislationNumber,
            String legislationUrl,
            String sourceDataUrl,
            Instant startDateUtc,
            Instant updateDateUtc,
            List<VoteMemberJson> members) {}

    private void printJsonList(List<? extends Message> messages) throws IOException {
        JsonFormat.Printer printer = JsonFormat.printer()
                .preservingProtoFieldNames()
                .includingDefaultValueFields();
        List<String> jsonEntries = new ArrayList<>(messages.size());
        for (Message message : messages) {
            jsonEntries.add(printToString(printer, message));
        }
        if (jsonEntries.isEmpty()) {
            out.println("[]");
            return;
        }
        String joined = jsonEntries.stream()
                .map(json -> "  " + json)
                .collect(Collectors.joining(",\n"));
        out.println("[");
        out.println(joined);
        out.println("]");
    }

    private void printJson(Message message) throws IOException {
        JsonFormat.Printer printer = JsonFormat.printer()
                .preservingProtoFieldNames()
                .includingDefaultValueFields();
        out.println(printToString(printer, message));
    }

    private String printToString(JsonFormat.Printer printer, Message message) throws IOException {
        StringWriter writer = new StringWriter();
        printer.appendTo(message, writer);
        return writer.toString();
    }

    private void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        PrintWriter writer = new PrintWriter(out, true);
        formatter.printHelp(
                writer,
                120,
                "congress-cli",
                "Options:",
                options,
                formatter.getLeftPadding(),
                formatter.getDescPadding(),
                "Examples:\n" +
                        "  congress-cli --operation list-chambers --key-file gradle.properties\n" +
                        "  congress-cli --operation list-congresses\n" +
                        "  congress-cli --operation list-votes --congress 118 --session 2 --key-file gradle.properties\n" +
                        "  congress-cli --operation vote-members --congress 118 --session 2 --vote-number 17 --format json\n" +
                        "  congress-cli --operation member-details --memberId A000360 --format json",
                true
        );
        writer.flush();
    }

    private Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder("o")
                .longOpt("operation")
                .hasArg()
                .argName("name")
                .desc("Operation to run: list-chambers, list-members, member-details, list-congresses, list-votes, or vote-members")
                .required()
                .build());
        options.addOption(Option.builder("k")
                .longOpt("key-file")
                .hasArg()
                .argName("file")
                .desc("Path to properties file that provides CONGRESS_API_KEY (defaults to gradle.properties)")
                .build());
        options.addOption(Option.builder()
                .longOpt("chamber")
                .hasArg()
                .argName("name")
                .desc("Chamber for list-members: house/lower or senate/upper")
                .build());
        options.addOption(Option.builder()
                .longOpt("current")
                .hasArg()
                .argName("true/false")
                .desc("Whether to restrict to current members (default true)")
                .build());
        options.addOption(Option.builder()
                .longOpt("last-years")
                .hasArg()
                .argName("number")
                .desc("Limit to members whose term started within the past N years")
                .build());
        options.addOption(Option.builder()
                .longOpt("memberId")
                .hasArg()
                .argName("id")
                .desc("Biographical member identifier for member-details")
                .build());
        options.addOption(Option.builder()
                .longOpt("session")
                .hasArg()
                .argName("number")
                .desc("House session number when listing votes or vote members (1 or 2)")
                .build());
        options.addOption(Option.builder()
                .longOpt("vote-number")
                .hasArg()
                .argName("rollCall")
                .desc("Roll call number when retrieving vote members")
                .build());
        options.addOption(Option.builder()
                .longOpt("congress")
                .hasArg()
                .argName("number")
                .desc("Congress number to query (default 118)")
                .build());
        options.addOption(Option.builder()
                .longOpt("format")
                .hasArg()
                .argName("type")
                .desc("Output format: pretty (default) or json")
                .build());
        options.addOption(Option.builder()
                .longOpt("show-url")
                .desc("Prints the Congress.gov request URL after results")
                .build());
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Prints usage information")
                .build());
        return options;
    }

    private boolean hasHelpFlag(String[] args) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private enum Operation {
        LIST_CHAMBERS("list-chambers"),
        LIST_MEMBERS("list-members"),
        MEMBER_DETAILS("member-details"),
        LIST_CONGRESSES("list-congresses"),
        LIST_VOTES("list-votes"),
        VOTE_MEMBERS("vote-members");

        private final String flag;

        Operation(String flag) {
            this.flag = flag;
        }

        static Operation from(String rawValue) {
            if (rawValue == null) {
                throw new IllegalArgumentException("Operation is required.");
            }
            String normalized = rawValue.trim().toLowerCase(Locale.US);
            for (Operation value : values()) {
                if (value.flag.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown operation '" + rawValue + "'.");
        }
    }

    private enum OutputFormat {
        PRETTY("pretty"),
        JSON("json");

        private final String flag;

        OutputFormat(String flag) {
            this.flag = flag;
        }

        static OutputFormat from(String rawValue) {
            String normalized = rawValue.trim().toLowerCase(Locale.US);
            for (OutputFormat value : values()) {
                if (value.flag.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown format '" + rawValue + "'.");
        }
    }

    /** Minimal timestamp helper that avoids pulling in additional protobuf classes at runtime. */
    private static final class TimestampFormatter {
        private TimestampFormatter() {}

        static String format(long epochSeconds) {
            if (epochSeconds <= 0) {
                return "-";
            }
            return java.time.Instant.ofEpochSecond(epochSeconds).toString();
        }
    }

    static final class TableRenderer {
        private final List<String> headers;
        private final List<List<String>> rows = new ArrayList<>();

        TableRenderer(List<String> headers) {
            this.headers = new ArrayList<>(headers);
        }

        void addRow(List<String> row) {
            rows.add(new ArrayList<>(row));
        }

        String render() {
            List<Integer> widths = new ArrayList<>(headers.size());
            for (int i = 0; i < headers.size(); i++) {
                widths.add(headers.get(i).length());
            }
            for (List<String> row : rows) {
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < row.size() ? row.get(i) : "";
                    widths.set(i, Math.max(widths.get(i), value.length()));
                }
            }

            StringBuilder builder = new StringBuilder();
            String border = buildBorder(widths);
            builder.append(border);
            builder.append(System.lineSeparator());
            builder.append(formatRow(headers, widths));
            builder.append(System.lineSeparator());
            builder.append(border);
            builder.append(System.lineSeparator());
            for (List<String> row : rows) {
                builder.append(formatRow(row, widths));
                builder.append(System.lineSeparator());
            }
            builder.append(border);
            return builder.toString();
        }

        private String buildBorder(List<Integer> widths) {
            return widths.stream()
                    .map(width -> "-".repeat(width + 2))
                    .collect(Collectors.joining("+", "+", "+"));
        }

        private String formatRow(List<String> columns, List<Integer> widths) {
            StringBuilder builder = new StringBuilder();
            builder.append("|");
            for (int i = 0; i < widths.size(); i++) {
                String value = i < columns.size() ? columns.get(i) : "";
                builder.append(" ").append(padRight(value, widths.get(i))).append(" |");
            }
            return builder.toString();
        }

        private String padRight(String value, int width) {
            if (value.length() >= width) {
                return value;
            }
            return value + " ".repeat(width - value.length());
        }
    }
}
