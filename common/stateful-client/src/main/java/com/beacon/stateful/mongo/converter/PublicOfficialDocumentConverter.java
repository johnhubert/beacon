package com.beacon.stateful.mongo.converter;

import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.google.protobuf.Timestamp;
import java.util.Date;
import org.bson.Document;

/**
 * Maps protobuf PublicOfficial payloads to BSON documents for Mongo persistence.
 */
public final class PublicOfficialDocumentConverter {

    private PublicOfficialDocumentConverter() {}

    public static Document toDocument(PublicOfficial official) {
        Document document = new Document()
                .append("_id", official.getUuid())
                .append("source_id", official.getSourceId())
                .append("legislative_body_uuid", official.getLegislativeBodyUuid())
                .append("full_name", official.getFullName())
                .append("party_affiliation", official.getPartyAffiliation())
                .append("role_title", official.getRoleTitle())
                .append("jurisdiction_region_code", official.getJurisdictionRegionCode())
                .append("district_identifier", official.getDistrictIdentifier())
                .append("office_status", official.getOfficeStatus().name())
                .append("biography_url", official.getBiographyUrl())
                .append("photo_url", official.getPhotoUrl())
                .append("version_hash", official.getVersionHash());

        ProtoTimestampConverter.toDate(official.hasTermStartDate() ? official.getTermStartDate() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("term_start_date", date));
        ProtoTimestampConverter.toDate(official.hasTermEndDate() ? official.getTermEndDate() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("term_end_date", date));
        ProtoTimestampConverter.toDate(official.hasLastRefreshedAt() ? official.getLastRefreshedAt() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("last_refreshed_at", date));

        return document;
    }

    public static PublicOfficial toProto(Document document) {
        PublicOfficial.Builder builder = PublicOfficial.newBuilder()
                .setUuid(document.getString("_id"))
                .setSourceId(document.getString("source_id"))
                .setLegislativeBodyUuid(document.getString("legislative_body_uuid"))
                .setFullName(document.getString("full_name"))
                .setPartyAffiliation(document.getString("party_affiliation"))
                .setRoleTitle(document.getString("role_title"))
                .setJurisdictionRegionCode(document.getString("jurisdiction_region_code"))
                .setDistrictIdentifier(document.getString("district_identifier"))
                .setOfficeStatus(OfficeStatus.valueOf(document.getString("office_status")))
                .setBiographyUrl(document.getString("biography_url"))
                .setPhotoUrl(document.getString("photo_url"));

        String versionHash = document.getString("version_hash");
        if (versionHash != null) {
            builder.setVersionHash(versionHash);
        }

        ProtoTimestampConverter.toTimestamp(document.getDate("term_start_date")).ifPresent(builder::setTermStartDate);
        ProtoTimestampConverter.toTimestamp(document.getDate("term_end_date")).ifPresent(builder::setTermEndDate);
        ProtoTimestampConverter.toTimestamp(document.getDate("last_refreshed_at")).ifPresent(builder::setLastRefreshedAt);

        return builder.build();
    }
}
