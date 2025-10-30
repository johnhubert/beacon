package com.beacon.rest.officials.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.rest.officials.mapper.OfficialMapper;
import com.beacon.rest.officials.model.AttendanceSnapshotResponse;
import com.beacon.rest.officials.model.OfficialDetail;
import com.beacon.rest.officials.model.OfficialSummary;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Provides read-only access to public officials stored within MongoDB.
 */
@Service
public class OfficialService {

    private final PublicOfficialRepository publicOfficialRepository;

    public OfficialService(PublicOfficialRepository publicOfficialRepository) {
        this.publicOfficialRepository = publicOfficialRepository;
    }

    /**
     * Fetches at most {@code limit} officials ordered by the natural MongoDB ordering.
     *
     * @param limit optional limit; when {@code null} the default of 50 is applied
     * @return list of summaries presented in the UI
     */
    public List<OfficialSummary> fetchOfficials(Integer limit) {
        int resolvedLimit = limit == null ? 50 : limit;
        Assert.isTrue(resolvedLimit >= 0, "limit must not be negative");

        List<PublicOfficial> officials = publicOfficialRepository.findAll(resolvedLimit);
        return officials.stream()
                .map(OfficialMapper::toSummary)
                .collect(Collectors.toList());
    }

    /**
     * Loads a specific official by source identifier.
     *
     * @param sourceId stable identifier, for example a Bioguide ID
     * @return official detail record when present
     */
    public Optional<OfficialDetail> findOfficial(String sourceId) {
        Assert.hasText(sourceId, "sourceId must be provided");
        return publicOfficialRepository.findOfficialBySourceId(sourceId)
                .map(OfficialMapper::toDetail);
    }

    /**
     * Retrieves attendance history snapshots for the supplied official.
     */
    public Optional<List<AttendanceSnapshotResponse>> findAttendanceHistory(String sourceId) {
        Assert.hasText(sourceId, "sourceId must be provided");
        return publicOfficialRepository.findOfficialBySourceId(sourceId)
                .map(PublicOfficial::getAttendanceHistoryList)
                .map(history -> history.stream()
                        .map(OfficialMapper::toSnapshotResponse)
                        .collect(Collectors.toList()));
    }
}
