package com.beacon.stateful.mongo;

import java.util.Optional;

import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.stateful.mongo.converter.PublicOfficialDocumentConverter;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

public class PublicOfficialRepository {

    private final MongoCollection<Document> collection;

    public PublicOfficialRepository(MongoCollection<Document> collection) {
        this.collection = collection;
        ensureIndexes();
    }

    private void ensureIndexes() {
        collection.createIndex(Indexes.ascending("source_id"), new IndexOptions().unique(true));
        collection.createIndex(Indexes.ascending("legislative_body_uuid"));
    }

    public InsertOneResult addOfficial(PublicOfficial official) {
        Document document = PublicOfficialDocumentConverter.toDocument(official);
        return collection.insertOne(document);
    }

    public boolean existsBySourceId(String sourceId) {
        long count = collection.countDocuments(Filters.eq("source_id", sourceId), new CountOptions().limit(1));
        return count > 0;
    }

    public boolean updateOfficial(PublicOfficial official) {
        Document document = PublicOfficialDocumentConverter.toDocument(official);
        UpdateResult result = collection.replaceOne(Filters.eq("source_id", official.getSourceId()), document, new ReplaceOptions().upsert(false));
        return result.getMatchedCount() > 0;
    }

    public void upsertOfficial(PublicOfficial official) {
        Document document = PublicOfficialDocumentConverter.toDocument(official);
        collection.replaceOne(Filters.eq("source_id", official.getSourceId()), document, new ReplaceOptions().upsert(true));
    }

    public Optional<OfficialMetadata> findMetadataBySourceId(String sourceId) {
        Document document = collection
                .find(Filters.eq("source_id", sourceId))
                .projection(Projections.include("_id", "version_hash"))
                .first();
        if (document == null) {
            return Optional.empty();
        }
        Object idValue = document.get("_id");
        String uuid = idValue instanceof String ? (String) idValue : idValue != null ? idValue.toString() : null;
        String versionHash = document.getString("version_hash");
        return Optional.of(new OfficialMetadata(uuid, versionHash));
    }

    public boolean deleteBySourceId(String sourceId) {
        DeleteResult result = collection.deleteOne(Filters.eq("source_id", sourceId));
        return result.getDeletedCount() > 0;
    }

    public long countByLegislativeBody(String legislativeBodyUuid) {
        return collection.countDocuments(Filters.eq("legislative_body_uuid", legislativeBodyUuid));
    }

    public record OfficialMetadata(String uuid, String versionHash) {}
}
