package com.beacon.stateful.mongo;

import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.stateful.mongo.converter.LegislativeBodyDocumentConverter;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;

public class LegislativeBodyRepository {

    private final MongoCollection<Document> collection;

    public LegislativeBodyRepository(MongoCollection<Document> collection) {
        this.collection = collection;
        ensureIndexes();
    }

    private void ensureIndexes() {
        collection.createIndex(Indexes.ascending("source_id"), new IndexOptions().unique(true));
    }

    public void upsert(LegislativeBody legislativeBody) {
        Document document = LegislativeBodyDocumentConverter.toDocument(legislativeBody);
        collection.replaceOne(Filters.eq("source_id", legislativeBody.getSourceId()), document, new ReplaceOptions().upsert(true));
    }

    public Optional<Instant> findRosterLastRefreshedAt(String sourceId) {
        Document document = collection.find(Filters.eq("source_id", sourceId))
                .projection(Projections.include("roster_last_refreshed_at"))
                .first();
        if (document == null) {
            return Optional.empty();
        }
        Date date = document.getDate("roster_last_refreshed_at");
        return date == null ? Optional.empty() : Optional.of(date.toInstant());
    }

    public void updateRosterLastRefreshedAt(String sourceId, Instant refreshedAt) {
        collection.updateOne(
                Filters.eq("source_id", sourceId),
                Updates.set("roster_last_refreshed_at", Date.from(refreshedAt)));
    }
}
