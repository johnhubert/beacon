package com.beacon.stateful.mongo;

import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.stateful.mongo.converter.LegislativeBodyDocumentConverter;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
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
}
