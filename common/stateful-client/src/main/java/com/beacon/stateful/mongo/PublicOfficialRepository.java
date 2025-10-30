package com.beacon.stateful.mongo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import com.mongodb.client.model.Sorts;
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
        collection.createIndex(Indexes.ascending("full_name"));
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
                .projection(Projections.include("_id", "version_hash", "last_refreshed_at"))
                .first();
        if (document == null) {
            return Optional.empty();
        }
        Object idValue = document.get("_id");
        String uuid = idValue instanceof String ? (String) idValue : idValue != null ? idValue.toString() : null;
        String versionHash = document.getString("version_hash");
        Date lastRefreshed = document.getDate("last_refreshed_at");
        Instant refreshedAt = lastRefreshed == null ? null : lastRefreshed.toInstant();
        return Optional.of(new OfficialMetadata(uuid, versionHash, refreshedAt));
    }

    /**
     * Returns the persisted official for the supplied source identifier when present.
     *
     * @param sourceId stable source identifier, e.g. a Bioguide ID
     * @return optional official instance
     */
    public Optional<PublicOfficial> findOfficialBySourceId(String sourceId) {
        Document document = collection.find(Filters.eq("source_id", sourceId)).first();
        if (document == null) {
            return Optional.empty();
        }
        return Optional.of(PublicOfficialDocumentConverter.toProto(document));
    }

    /**
     * Retrieves a list of officials with an optional limit. When {@code limit} is less than or equal to zero the
     * entire collection is returned.
     *
     * @param limit maximum number of officials to return, or {@code <= 0} for all results
     * @return list of officials
     */
    public List<PublicOfficial> findAll(int limit) {
        return findAllOrderedByName(limit, 0);
    }

    /**
     * Retrieves officials ordered by their full name with optional pagination controls.
     *
     * @param limit maximum number of officials to return; when {@code <= 0} all remaining officials are returned
     * @param offset number of officials to skip from the beginning of the sorted set
     * @return ordered list of officials
     */
    public List<PublicOfficial> findAllOrderedByName(int limit, int offset) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }

        List<PublicOfficial> results = new ArrayList<>();
        com.mongodb.client.FindIterable<Document> iterable = collection.find()
                .sort(Sorts.ascending("full_name"));
        if (offset > 0) {
            iterable = iterable.skip(offset);
        }
        if (limit > 0) {
            iterable = iterable.limit(limit);
        }
        for (Document document : iterable) {
            results.add(PublicOfficialDocumentConverter.toProto(document));
        }
        return results;
    }

    public boolean deleteBySourceId(String sourceId) {
        DeleteResult result = collection.deleteOne(Filters.eq("source_id", sourceId));
        return result.getDeletedCount() > 0;
    }

    public long countByLegislativeBody(String legislativeBodyUuid) {
        return collection.countDocuments(Filters.eq("legislative_body_uuid", legislativeBodyUuid));
    }

    public Optional<Instant> findLatestRefreshTimestamp(String legislativeBodyUuid) {
        Document document = collection.find(Filters.eq("legislative_body_uuid", legislativeBodyUuid))
                .projection(Projections.include("last_refreshed_at"))
                .sort(Sorts.descending("last_refreshed_at"))
                .limit(1)
                .first();
        if (document == null) {
            return Optional.empty();
        }
        Date date = document.getDate("last_refreshed_at");
        return date == null ? Optional.empty() : Optional.of(date.toInstant());
    }

    public record OfficialMetadata(String uuid, String versionHash, Instant lastRefreshedAt) {}
}
