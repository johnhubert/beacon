package com.beacon.stateful.mongo.converter;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.JurisdictionType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.google.protobuf.Timestamp;
import org.bson.Document;

public final class LegislativeBodyDocumentConverter {

    private LegislativeBodyDocumentConverter() {}

    public static Document toDocument(LegislativeBody body) {
        Document document = new Document()
                .append("_id", body.getUuid())
                .append("source_id", body.getSourceId())
                .append("jurisdiction_type", body.getJurisdictionType().name())
                .append("jurisdiction_code", body.getJurisdictionCode())
                .append("name", body.getName())
                .append("chamber_type", body.getChamberType().name())
                .append("session", body.getSession());
        ProtoTimestampConverter.toDate(body.hasRosterLastRefreshedAt() ? body.getRosterLastRefreshedAt() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("roster_last_refreshed_at", date));
        ProtoTimestampConverter.toDate(body.hasLastVoteIngestedAt() ? body.getLastVoteIngestedAt() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("last_vote_ingested_at", date));
        return document;
    }

    public static LegislativeBody toProto(Document document) {
        LegislativeBody.Builder builder = LegislativeBody.newBuilder()
                .setUuid(document.getString("_id"))
                .setSourceId(document.getString("source_id"))
                .setJurisdictionType(JurisdictionType.valueOf(document.getString("jurisdiction_type")))
                .setJurisdictionCode(document.getString("jurisdiction_code"))
                .setName(document.getString("name"))
                .setChamberType(ChamberType.valueOf(document.getString("chamber_type")))
                .setSession(document.getString("session"));
        ProtoTimestampConverter.toTimestamp(document.getDate("roster_last_refreshed_at")).ifPresent(builder::setRosterLastRefreshedAt);
        ProtoTimestampConverter.toTimestamp(document.getDate("last_vote_ingested_at")).ifPresent(builder::setLastVoteIngestedAt);
        return builder.build();
    }
}
