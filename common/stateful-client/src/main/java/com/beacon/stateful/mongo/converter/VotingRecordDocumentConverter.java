package com.beacon.stateful.mongo.converter;

import com.beacon.common.accountability.v1.MemberVote;
import com.beacon.common.accountability.v1.VotingRecord;
import com.beacon.common.accountability.v1.VotePosition;
import com.beacon.stateful.mongo.VotingRecordRepository.PersistedVotingRecord;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bson.Document;

/**
 * Translates {@link VotingRecord} protobuf payloads to MongoDB documents while preserving ingestion metadata.
 */
public final class VotingRecordDocumentConverter {

    private VotingRecordDocumentConverter() {}

    public static Document toDocument(PersistedVotingRecord record) {
        VotingRecord votingRecord = record.votingRecord();
        Document document = new Document()
                .append("_id", votingRecord.getUuid())
                .append("source_id", votingRecord.getSourceId())
                .append("legislative_body_uuid", votingRecord.getLegislativeBodyUuid())
                .append("subject_summary", votingRecord.getSubjectSummary())
                .append("bill_reference", votingRecord.getBillReference())
                .append("bill_uri", votingRecord.getBillUri())
                .append("roll_call_reference", votingRecord.getRollCallReference())
                .append("congress_number", record.congressNumber())
                .append("session_number", record.sessionNumber())
                .append("roll_call_number", record.rollCallNumber())
                .append("vote_result", record.result())
                .append("vote_type", record.voteType())
                .append("source_data_url", record.sourceDataUrl())
                .append("legislation_type", record.legislationType())
                .append("legislation_number", record.legislationNumber())
                .append("legislation_url", record.legislationUrl());

        ProtoTimestampConverter.toDate(votingRecord.hasVoteDateUtc() ? votingRecord.getVoteDateUtc() : Timestamp.getDefaultInstance())
                .ifPresent(date -> document.append("vote_date_utc", date));
        Optional.ofNullable(record.updateDateUtc()).map(Date::from).ifPresent(date -> document.append("update_date_utc", date));

        List<Document> memberVotes = votingRecord.getMemberVotesList().stream()
                .map(VotingRecordDocumentConverter::toDocument)
                .collect(Collectors.toList());
        document.append("member_votes", memberVotes);
        return document;
    }

    public static PersistedVotingRecord toPersistedVotingRecord(Document document) {
        VotingRecord.Builder builder = VotingRecord.newBuilder()
                .setUuid(document.getString("_id"))
                .setSourceId(document.getString("source_id"))
                .setLegislativeBodyUuid(document.getString("legislative_body_uuid"))
                .setSubjectSummary(document.getString("subject_summary"))
                .setBillReference(document.getString("bill_reference"))
                .setBillUri(document.getString("bill_uri"))
                .setRollCallReference(document.getString("roll_call_reference"));

        ProtoTimestampConverter.toTimestamp(document.getDate("vote_date_utc")).ifPresent(builder::setVoteDateUtc);

        List<Document> memberDocs = Optional.ofNullable(document.getList("member_votes", Document.class)).orElse(List.of());
        for (Document voteDocument : memberDocs) {
            builder.addMemberVotes(toMemberVote(builder.getUuid(), voteDocument));
        }

        Instant updateDate = Optional.ofNullable(document.getDate("update_date_utc"))
                .map(Date::toInstant)
                .orElse(null);

        return new PersistedVotingRecord(
                builder.build(),
                updateDate,
                document.getInteger("congress_number", 0),
                document.getInteger("session_number", 0),
                document.getInteger("roll_call_number", 0),
                document.getString("source_data_url"),
                document.getString("vote_result"),
                document.getString("vote_type"),
                document.getString("legislation_type"),
                document.getString("legislation_number"),
                document.getString("legislation_url"));
    }

    private static Document toDocument(MemberVote vote) {
        Document document = new Document()
                .append("uuid", vote.getUuid())
                .append("source_id", vote.getSourceId())
                .append("official_uuid", vote.getOfficialUuid())
                .append("voting_record_uuid", vote.getVotingRecordUuid())
                .append("vote_position", vote.getVotePosition().name())
                .append("group_position", vote.getGroupPosition())
                .append("notes", vote.getNotes());
        return document;
    }

    private static MemberVote toMemberVote(String votingRecordUuid, Document document) {
        MemberVote.Builder builder = MemberVote.newBuilder()
                .setUuid(document.getString("uuid"))
                .setSourceId(document.getString("source_id"))
                .setOfficialUuid(Optional.ofNullable(document.getString("official_uuid")).orElse(""))
                .setVotingRecordUuid(Optional.ofNullable(document.getString("voting_record_uuid")).orElse(votingRecordUuid))
                .setGroupPosition(Optional.ofNullable(document.getString("group_position")).orElse(""))
                .setNotes(Optional.ofNullable(document.getString("notes")).orElse(""));

        String votePosition = document.getString("vote_position");
        if (votePosition != null && !votePosition.isBlank()) {
            builder.setVotePosition(VotePosition.valueOf(votePosition));
        } else {
            builder.setVotePosition(VotePosition.VOTE_POSITION_UNSPECIFIED);
        }

        return builder.build();
    }
}
