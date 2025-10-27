package com.beacon.tools.congress.cli;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientConfig;
import com.beacon.congress.client.CongressGovClientException;
import com.google.protobuf.Message;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
            err.println("Error: API_CONGRESS_GOV_KEY is required. Provide it via the properties file or environment.");
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

        try {
            return switch (operation) {
                case LIST_CHAMBERS -> listChambers(client, congressNumber, format);
                case LIST_MEMBERS -> listMembers(client, commandLine, congressNumber, format);
                case MEMBER_DETAILS -> showMemberDetails(client, commandLine, congressNumber, format);
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
        String fromEnv = System.getenv("API_CONGRESS_GOV_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return Optional.ofNullable(properties.getProperty("API_CONGRESS_GOV_KEY"))
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

    private int listChambers(CongressGovClient client, int congressNumber, OutputFormat format)
            throws IOException {
        List<LegislativeBody> bodies = client.fetchLegislativeBodies(congressNumber);
        if (bodies.isEmpty()) {
            out.println("No legislative bodies returned by Congress.gov.");
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
        }
        return 0;
    }

    private int listMembers(CongressGovClient client, CommandLine commandLine, int congressNumber, OutputFormat format)
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

        List<PublicOfficial> officials = client.fetchMembers(congressNumber, chamberType);
        if (officials.isEmpty()) {
            out.println("No members returned for the requested chamber.");
            return 0;
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
        }
        return 0;
    }

    private int showMemberDetails(CongressGovClient client, CommandLine commandLine, int congressNumber,
            OutputFormat format) throws IOException {
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
                        "  congress-cli --operation list-members --chamber house --format pretty --key-file gradle.properties\n" +
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
                .desc("Operation to run: list-chambers, list-members, or member-details")
                .required()
                .build());
        options.addOption(Option.builder("k")
                .longOpt("key-file")
                .hasArg()
                .argName("file")
                .desc("Path to properties file that provides API_CONGRESS_GOV_KEY (defaults to gradle.properties)")
                .build());
        options.addOption(Option.builder()
                .longOpt("chamber")
                .hasArg()
                .argName("name")
                .desc("Chamber for list-members: house/lower or senate/upper")
                .build());
        options.addOption(Option.builder()
                .longOpt("memberId")
                .hasArg()
                .argName("id")
                .desc("Biographical member identifier for member-details")
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
        MEMBER_DETAILS("member-details");

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
