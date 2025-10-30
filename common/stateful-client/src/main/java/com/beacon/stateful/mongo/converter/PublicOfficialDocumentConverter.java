package com.beacon.stateful.mongo.converter;

import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.common.accountability.v1.AttendanceSummary;
import com.beacon.common.accountability.v1.AttendanceSnapshot;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

        if (official.hasAttendanceSummary()) {
            document.append("attendance_summary", toDocument(official.getAttendanceSummary()));
        }
        if (official.getAttendanceHistoryCount() > 0) {
            List<Document> historyDocuments = official.getAttendanceHistoryList().stream()
                    .map(PublicOfficialDocumentConverter::toDocument)
                    .collect(Collectors.toList());
            document.append("attendance_history", historyDocuments);
        }

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

        Optional.ofNullable(document.get("attendance_summary", Document.class))
                .map(PublicOfficialDocumentConverter::toAttendanceSummary)
                .ifPresent(builder::setAttendanceSummary);
        List<Document> historyDocuments = document.getList("attendance_history", Document.class);
        if (historyDocuments != null) {
            historyDocuments.stream()
                    .map(PublicOfficialDocumentConverter::toAttendanceSnapshot)
                    .forEach(builder::addAttendanceHistory);
        }

        return builder.build();
    }

    private static Document toDocument(AttendanceSummary summary) {
        return new Document()
                .append("sessions_attended", summary.getSessionsAttended())
                .append("sessions_total", summary.getSessionsTotal())
                .append("votes_participated", summary.getVotesParticipated())
                .append("votes_total", summary.getVotesTotal())
                .append("presence_score", summary.getPresenceScore())
                .append("participation_score", summary.getParticipationScore());
    }

    private static AttendanceSummary toAttendanceSummary(Document document) {
        return AttendanceSummary.newBuilder()
                .setSessionsAttended(document.getInteger("sessions_attended", 0))
                .setSessionsTotal(document.getInteger("sessions_total", 0))
                .setVotesParticipated(document.getInteger("votes_participated", 0))
                .setVotesTotal(document.getInteger("votes_total", 0))
                .setPresenceScore(document.getInteger("presence_score", 0))
                .setParticipationScore(document.getInteger("participation_score", 0))
                .build();
    }

    private static Document toDocument(AttendanceSnapshot snapshot) {
        Document document = new Document()
                .append("period_label", snapshot.getPeriodLabel())
                .append("sessions_attended", snapshot.getSessionsAttended())
                .append("sessions_total", snapshot.getSessionsTotal())
                .append("votes_participated", snapshot.getVotesParticipated())
                .append("votes_total", snapshot.getVotesTotal())
                .append("presence_score", snapshot.getPresenceScore())
                .append("participation_score", snapshot.getParticipationScore());
        ProtoTimestampConverter.toDate(snapshot.hasPeriodStart() ? snapshot.getPeriodStart() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("period_start", date));
        ProtoTimestampConverter.toDate(snapshot.hasPeriodEnd() ? snapshot.getPeriodEnd() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("period_end", date));
        return document;
    }

    private static AttendanceSnapshot toAttendanceSnapshot(Document document) {
        AttendanceSnapshot.Builder builder = AttendanceSnapshot.newBuilder()
                .setPeriodLabel(document.getString("period_label"))
                .setSessionsAttended(document.getInteger("sessions_attended", 0))
                .setSessionsTotal(document.getInteger("sessions_total", 0))
                .setVotesParticipated(document.getInteger("votes_participated", 0))
                .setVotesTotal(document.getInteger("votes_total", 0))
                .setPresenceScore(document.getInteger("presence_score", 0))
                .setParticipationScore(document.getInteger("participation_score", 0));
        ProtoTimestampConverter.toTimestamp(document.getDate("period_start"))
                .ifPresent(builder::setPeriodStart);
        ProtoTimestampConverter.toTimestamp(document.getDate("period_end"))
                .ifPresent(builder::setPeriodEnd);
        return builder.build();
    }
}
