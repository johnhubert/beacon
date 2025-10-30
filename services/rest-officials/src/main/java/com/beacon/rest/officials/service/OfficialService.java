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
     * Retrieves a page of officials ordered by display name to provide deterministic feeds.
     *
     * @param page zero-based page index to retrieve; defaults to {@code 0} when {@code null}
     * @param pageSize maximum number of officials to return; defaults to {@code 25} when {@code null}
     * @return list of summaries presented in the UI
     */
    public List<OfficialSummary> fetchOfficials(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedPageSize = pageSize == null ? 25 : pageSize;
        Assert.isTrue(resolvedPage >= 0, "page must not be negative");
        Assert.isTrue(resolvedPageSize > 0, "pageSize must be positive");

        long offsetLong = (long) resolvedPage * resolvedPageSize;
        Assert.isTrue(offsetLong <= Integer.MAX_VALUE, "Requested page exceeds supported range");
        int offset = (int) offsetLong;

        List<PublicOfficial> officials = publicOfficialRepository.findAllOrderedByName(resolvedPageSize, offset);
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
