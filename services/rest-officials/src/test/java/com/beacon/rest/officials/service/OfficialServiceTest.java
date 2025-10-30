package com.beacon.rest.officials.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.beacon.stateful.mongo.PublicOfficialRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialServiceTest {

    private PublicOfficialRepository publicOfficialRepository;
    private OfficialService officialService;

    @BeforeEach
    void setUp() {
        publicOfficialRepository = mock(PublicOfficialRepository.class);
        when(publicOfficialRepository.findAllOrderedByName(anyInt(), anyInt())).thenReturn(List.of());
        officialService = new OfficialService(publicOfficialRepository);
    }

    @Test
    void fetchOfficialsUsesDefaultPagination() {
        officialService.fetchOfficials(null, null);

        verify(publicOfficialRepository).findAllOrderedByName(eq(25), eq(0));
    }

    @Test
    void fetchOfficialsCalculatesOffsetBasedOnPage() {
        officialService.fetchOfficials(2, 10);

        verify(publicOfficialRepository).findAllOrderedByName(eq(10), eq(20));
    }

    @Test
    void fetchOfficialsRejectsNegativeParameters() {
        assertThatThrownBy(() -> officialService.fetchOfficials(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must not be negative");

        assertThatThrownBy(() -> officialService.fetchOfficials(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize must be positive");
    }
}
