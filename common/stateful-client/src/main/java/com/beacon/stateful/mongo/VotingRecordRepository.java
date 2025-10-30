package com.beacon.stateful.mongo;

import com.beacon.common.accountability.v1.VotingRecord;
import com.beacon.stateful.mongo.converter.VotingRecordDocumentConverter;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;

/**
 * Persists ingested roll call voting records so expensive remote lookups can be avoided on subsequent runs.
 */
public class VotingRecordRepository {

    private final MongoCollection<Document> collection;

    public VotingRecordRepository(MongoCollection<Document> collection) {
        this.collection = collection;
        ensureIndexes();
    }

    private void ensureIndexes() {
        collection.createIndex(Indexes.ascending("source_id"), new IndexOptions().unique(true));
        collection.createIndex(Indexes.ascending("legislative_body_uuid", "vote_date_utc"));
        collection.createIndex(Indexes.ascending("update_date_utc"));
    }

    public void upsert(PersistedVotingRecord record) {
        Document document = VotingRecordDocumentConverter.toDocument(record);
        collection.replaceOne(Filters.eq("source_id", record.votingRecord().getSourceId()), document, new ReplaceOptions().upsert(true));
    }

    public Optional<RecordMetadata> findMetadataBySourceId(String sourceId) {
        Document document = collection.find(Filters.eq("source_id", sourceId))
                .projection(Projections.include("update_date_utc", "congress_number", "session_number", "roll_call_number"))
                .first();
        if (document == null) {
            return Optional.empty();
        }
        Date update = document.getDate("update_date_utc");
        Instant updateInstant = update == null ? null : update.toInstant();
        return Optional.of(new RecordMetadata(
                updateInstant,
                document.getInteger("congress_number", 0),
                document.getInteger("session_number", 0),
                document.getInteger("roll_call_number", 0)));
    }

    public Optional<PersistedVotingRecord> findBySourceId(String sourceId) {
        Document document = collection.find(Filters.eq("source_id", sourceId)).first();
        if (document == null) {
            return Optional.empty();
        }
        return Optional.of(VotingRecordDocumentConverter.toPersistedVotingRecord(document));
    }

    public List<PersistedVotingRecord> findByLegislativeBody(String legislativeBodyUuid, int limit) {
        List<PersistedVotingRecord> results = new ArrayList<>();
        var iterable = collection.find(Filters.eq("legislative_body_uuid", legislativeBodyUuid))
                .sort(Sorts.ascending("vote_date_utc"));
        if (limit > 0) {
            iterable = iterable.limit(limit);
        }
        for (Document document : iterable) {
            results.add(VotingRecordDocumentConverter.toPersistedVotingRecord(document));
        }
        return results;
    }

    public List<PersistedVotingRecord> findByLegislativeBodyUpdatedAfter(String legislativeBodyUuid, Instant updatedAfter) {
        List<PersistedVotingRecord> results = new ArrayList<>();
        var filter = updatedAfter == null
                ? Filters.eq("legislative_body_uuid", legislativeBodyUuid)
                : Filters.and(
                        Filters.eq("legislative_body_uuid", legislativeBodyUuid),
                        Filters.gt("update_date_utc", Date.from(updatedAfter)));
        var iterable = collection.find(filter).sort(Sorts.ascending("vote_date_utc"));
        for (Document document : iterable) {
            results.add(VotingRecordDocumentConverter.toPersistedVotingRecord(document));
        }
        return results;
    }

    public record RecordMetadata(Instant updateDate, int congressNumber, int sessionNumber, int rollCallNumber) {}

    public record PersistedVotingRecord(
            VotingRecord votingRecord,
            Instant updateDateUtc,
            int congressNumber,
            int sessionNumber,
            int rollCallNumber,
            String sourceDataUrl,
            String result,
            String voteType,
            String legislationType,
            String legislationNumber,
            String legislationUrl) {}
}
