package com.beacon.stateful.mongo.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class PublicOfficialDocumentConverterTest {

    @Test
    void roundTripConversionPreservesCoreFields() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        PublicOfficial official = PublicOfficial.newBuilder()
                .setUuid("uuid-123")
                .setSourceId("S000001")
                .setLegislativeBodyUuid("body-1")
                .setFullName("Test Official")
                .setPartyAffiliation("I")
                .setRoleTitle("Senator")
                .setJurisdictionRegionCode("US")
                .setDistrictIdentifier("XX")
                .setOfficeStatus(OfficeStatus.ACTIVE)
                .setBiographyUrl("https://example.org/bio")
                .setPhotoUrl("https://example.org/photo.jpg")
                .setTermStartDate(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                .build();

        Document document = PublicOfficialDocumentConverter.toDocument(official);
        PublicOfficial hydrated = PublicOfficialDocumentConverter.toProto(document);

        assertThat(hydrated.getUuid()).isEqualTo(official.getUuid());
        assertThat(hydrated.getSourceId()).isEqualTo(official.getSourceId());
        assertThat(hydrated.getFullName()).isEqualTo(official.getFullName());
        assertThat(hydrated.getOfficeStatus()).isEqualTo(OfficeStatus.ACTIVE);
        assertThat(hydrated.getBiographyUrl()).isEqualTo("https://example.org/bio");
        assertThat(hydrated.getTermStartDate().getSeconds()).isEqualTo(now.getEpochSecond());
    }
}
