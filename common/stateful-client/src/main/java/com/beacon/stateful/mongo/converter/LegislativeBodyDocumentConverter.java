package com.beacon.stateful.mongo.converter;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.JurisdictionType;
import com.beacon.common.accountability.v1.LegislativeBody;
import org.bson.Document;

public final class LegislativeBodyDocumentConverter {

    private LegislativeBodyDocumentConverter() {}

    public static Document toDocument(LegislativeBody body) {
        return new Document()
                .append("_id", body.getUuid())
                .append("source_id", body.getSourceId())
                .append("jurisdiction_type", body.getJurisdictionType().name())
                .append("jurisdiction_code", body.getJurisdictionCode())
                .append("name", body.getName())
                .append("chamber_type", body.getChamberType().name())
                .append("session", body.getSession());
    }

    public static LegislativeBody toProto(Document document) {
        return LegislativeBody.newBuilder()
                .setUuid(document.getString("_id"))
                .setSourceId(document.getString("source_id"))
                .setJurisdictionType(JurisdictionType.valueOf(document.getString("jurisdiction_type")))
                .setJurisdictionCode(document.getString("jurisdiction_code"))
                .setName(document.getString("name"))
                .setChamberType(ChamberType.valueOf(document.getString("chamber_type")))
                .setSession(document.getString("session"))
                .build();
    }
}
