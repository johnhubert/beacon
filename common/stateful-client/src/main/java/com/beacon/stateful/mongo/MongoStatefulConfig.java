package com.beacon.stateful.mongo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration holder for the shared Mongo client. Values are lazily resolved from process
 * environment variables so every service can connect without bespoke YAML.
 *
 * <p>The following variables influence the resulting {@code connectionString}:
 *
 * <ul>
 *   <li>{@code STATEFUL_MONGO_URI} – full Mongo connection string (takes precedence over all other
 *       options).
 *   <li>{@code STATEFUL_MONGO_HOST} / {@code STATEFUL_MONGO_PORT} – host and port used to compose a
 *       standard {@code mongodb://host:port} URI when {@code STATEFUL_MONGO_URI} is absent.
 *   <li>{@code STATEFUL_MONGO_TLS_CA_FILE} &amp; {@code STATEFUL_MONGO_TLS_CERT_KEY_FILE} – absolute
 *       paths to the mounted CA bundle and client cert+key PEM file. When both are provided, the URI
 *       automatically enables TLS/mTLS by appending {@code tls=true}, {@code tlsCAFile}, and
 *       {@code tlsCertificateKeyFile} parameters.
 * </ul>
 *
 * <p>The database name defaults to {@code accountability_stateful} but can be overridden via
 * {@code STATEFUL_MONGO_DATABASE}.
 */
public record MongoStatefulConfig(String connectionString, String databaseName) {

    private static final String DEFAULT_URI = "mongodb://mongo:27017";
    private static final String DEFAULT_DATABASE = "accountability_stateful";

    /**
     * Builds a configuration object from the supplied environment map. Callers may pass a subset of
     * {@link System#getenv()} when they want to override specific values during tests.
     */
    public static MongoStatefulConfig fromEnvironment(Map<String, String> env) {
        String uri = env.getOrDefault("STATEFUL_MONGO_URI", "");
        if (uri == null || uri.isBlank()) {
            String host = env.getOrDefault("STATEFUL_MONGO_HOST", "mongo");
            String port = env.getOrDefault("STATEFUL_MONGO_PORT", "27017");
            uri = "mongodb://%s:%s".formatted(host, port);
        }

        String database = env.getOrDefault("STATEFUL_MONGO_DATABASE", DEFAULT_DATABASE);

        Optional<String> tlsCa = optional(env.get("STATEFUL_MONGO_TLS_CA_FILE"));
        Optional<String> tlsCertKey = optional(env.get("STATEFUL_MONGO_TLS_CERT_KEY_FILE"));

        if (tlsCa.isPresent() && tlsCertKey.isPresent()) {
            uri = appendTlsParams(uri, tlsCa.get(), tlsCertKey.get());
        }

        return new MongoStatefulConfig(uri, database);
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    private static String appendTlsParams(String uri, String caFile, String certKeyFile) {
        StringBuilder builder = new StringBuilder(uri);
        if (!uri.contains("?")) {
            builder.append('?');
        } else if (!uri.endsWith("&") && !uri.endsWith("?")) {
            builder.append('&');
        }
        builder.append("tls=true")
                .append('&')
                .append("tlsCAFile=")
                .append(encode(caFile))
                .append('&')
                .append("tlsCertificateKeyFile=")
                .append(encode(certKeyFile));
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Convenience factory that resolves values directly from {@link System#getenv()}.
     */
    public static MongoStatefulConfig createDefault() {
        return fromEnvironment(System.getenv());
    }
}
