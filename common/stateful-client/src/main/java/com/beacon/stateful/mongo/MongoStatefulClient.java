package com.beacon.stateful.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around the MongoDB driver that exposes strongly typed repositories for any
 * microservice that needs stateful storage.
 *
 * <p>The client is typically created through {@link MongoStatefulConfig#createDefault()} so that it
 * honors the shared environment variables:
 *
 * <ul>
 *   <li>{@code STATEFUL_MONGO_URI} or {@code STATEFUL_MONGO_HOST}/{@code STATEFUL_MONGO_PORT} to
 *       select the target server.
 *   <li>{@code STATEFUL_MONGO_DATABASE} to choose the logical database.
 *   <li>{@code STATEFUL_MONGO_TLS_CA_FILE} and {@code STATEFUL_MONGO_TLS_CERT_KEY_FILE} to
 *       transparently enable TLS/mTLS without custom driver code in each service.
 * </ul>
 *
 * <p>The Spring configuration in {@code services/ingest-usa-fed} wires this client as a singleton so
 * any microservice can inject {@link PublicOfficialRepository}, {@link LegislativeBodyRepository}, or
 * {@link VotingRecordRepository} without worrying about credentials or driver initialization.
 */
public final class MongoStatefulClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoStatefulClient.class);

    private final MongoStatefulConfig config;
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final PublicOfficialRepository publicOfficialRepository;
    private final LegislativeBodyRepository legislativeBodyRepository;
    private final VotingRecordRepository votingRecordRepository;

    /**
     * Creates a client using the given configuration. Most callers should prefer
     * {@link MongoStatefulConfig#createDefault()} so that environment variables drive behavior.
     */
    public MongoStatefulClient(MongoStatefulConfig config) {
        this(config, createMongoClient(config));
    }

    public MongoStatefulClient(MongoStatefulConfig config, MongoClient mongoClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.mongoClient = Objects.requireNonNull(mongoClient, "mongoClient");
        this.database = mongoClient.getDatabase(config.databaseName());
        this.publicOfficialRepository = new PublicOfficialRepository(database.getCollection("public_officials"));
        this.legislativeBodyRepository = new LegislativeBodyRepository(database.getCollection("legislative_bodies"));
        this.votingRecordRepository = new VotingRecordRepository(database.getCollection("legislative_body_votes"));
    }

    private static MongoClient createMongoClient(MongoStatefulConfig config) {
        ConnectionString connectionString = new ConnectionString(config.connectionString());
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        LOGGER.info("Connecting to MongoDB at {} (db: {})", connectionString.getHosts(), config.databaseName());
        return MongoClients.create(settings);
    }

    public PublicOfficialRepository publicOfficials() {
        return publicOfficialRepository;
    }

    public LegislativeBodyRepository legislativeBodies() {
        return legislativeBodyRepository;
    }

    public VotingRecordRepository votingRecords() {
        return votingRecordRepository;
    }

    public MongoDatabase database() {
        return database;
    }

    @Override
    public void close() throws IOException {
        mongoClient.close();
    }
}
