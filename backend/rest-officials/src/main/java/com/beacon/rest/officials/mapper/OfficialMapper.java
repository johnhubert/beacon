package com.beacon.rest.officials.mapper;

import java.time.Instant;
import java.util.Objects;

import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.rest.officials.model.OfficialDetail;
import com.beacon.rest.officials.model.OfficialSummary;
import com.google.protobuf.Timestamp;

/**
 * Transforms persisted {@link PublicOfficial} records into REST friendly
 * response objects.
 */
public final class OfficialMapper {

    private OfficialMapper() {
    }

    public static OfficialSummary toSummary(PublicOfficial official) {
        return new OfficialSummary(
                official.getUuid(),
                official.getSourceId(),
                official.getFullName(),
                official.getPartyAffiliation(),
                official.getRoleTitle(),
                official.getPhotoUrl(),
                toInstant(official.getLastRefreshedAt()));
    }

    public static OfficialDetail toDetail(PublicOfficial official) {
        return new OfficialDetail(
                official.getUuid(),
                official.getSourceId(),
                official.getLegislativeBodyUuid(),
                official.getFullName(),
                official.getPartyAffiliation(),
                official.getRoleTitle(),
                official.getJurisdictionRegionCode(),
                official.getDistrictIdentifier(),
                toInstant(official.getTermStartDate()),
                toInstant(official.getTermEndDate()),
                official.getOfficeStatus().name(),
                official.getBiographyUrl(),
                official.getPhotoUrl(),
                official.getVersionHash(),
                toInstant(official.getLastRefreshedAt()));
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || Objects.equals(timestamp, Timestamp.getDefaultInstance())) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
