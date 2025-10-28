package com.beacon.tools.congress.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CongressCliIntegrationTest {

    private static final String KEY_PROPERTY = "API_CONGRESS_GOV_KEY";

    @TempDir
    Path tempDir;

    private Path propertyFile;

    @BeforeAll
    void setupKeyFile() throws IOException {
        String apiKey = System.getProperty(KEY_PROPERTY, System.getenv(KEY_PROPERTY));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), KEY_PROPERTY + " must be provided to run CLI integration tests");

        Properties properties = new Properties();
        properties.setProperty(KEY_PROPERTY, apiKey);

        propertyFile = tempDir.resolve("cli-integration.properties");
        try (var writer = Files.newBufferedWriter(propertyFile)) {
            properties.store(writer, "Generated for CLI integration tests");
        }
    }

    private CliResult runCli(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout); PrintStream err = new PrintStream(stderr)) {
            CongressCli cli = new CongressCli(out, err);
            int exitCode = cli.execute(args);
            return new CliResult(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private String[] withKeyFile(String... arguments) {
        String[] combined = new String[arguments.length + 2];
        System.arraycopy(arguments, 0, combined, 0, arguments.length);
        combined[arguments.length] = "--key-file";
        combined[arguments.length + 1] = propertyFile.toString();
        return combined;
    }

    @Test
    @DisplayName("list-chambers pretty output returns results")
    void listChambersPretty() {
        CliResult result = runCli(withKeyFile("-o", "list-chambers"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Total records:");
        assertThat(result.stderr()).isBlank();
    }

    @Test
    @DisplayName("list-chambers json output returns array")
    void listChambersJson() {
        CliResult result = runCli(withKeyFile("-o", "list-chambers", "--format", "json"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).startsWith("[");
    }

    @Test
    @DisplayName("list-members house pretty output includes count")
    void listMembersHousePretty() {
        CliResult result = runCli(withKeyFile("-o", "list-members", "--chamber", "house"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Total records:");
        assertThat(extractRecordCount(result.stdout())).isGreaterThan(400);
    }

    @Test
    @DisplayName("list-members house json output returns array")
    void listMembersHouseJson() {
        CliResult result = runCli(withKeyFile("-o", "list-members", "--chamber", "house", "--format", "json"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).startsWith("[");
    }

    @Test
    @DisplayName("list-members senate pretty output includes approximately 100 entries")
    void listMembersSenatePretty() {
        CliResult result = runCli(withKeyFile("-o", "list-members", "--chamber", "upper"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Total records:");
        assertThat(extractRecordCount(result.stdout())).isGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("list-members senate json output returns array")
    void listMembersSenateJson() {
        CliResult result = runCli(withKeyFile("-o", "list-members", "--chamber", "upper", "--format", "json"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).startsWith("[");
    }

    @Test
    @DisplayName("member-details pretty output renders table")
    void memberDetailsPretty() {
        CliResult result = runCli(withKeyFile("-o", "member-details", "--memberId", "S000033"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Source ID").contains("Full Name");
    }

    @Test
    @DisplayName("member-details json output returns object")
    void memberDetailsJson() {
        CliResult result = runCli(withKeyFile("-o", "member-details", "--memberId", "S000033", "--format", "json"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).startsWith("{");
    }

    private int extractRecordCount(String output) {
        return output.lines()
                .filter(line -> line.startsWith("Total records:"))
                .findFirst()
                .map(line -> line.substring("Total records:".length()).trim())
                .map(Integer::parseInt)
                .orElse(0);
    }

    private record CliResult(int exitCode, String stdout, String stderr) {}
}
