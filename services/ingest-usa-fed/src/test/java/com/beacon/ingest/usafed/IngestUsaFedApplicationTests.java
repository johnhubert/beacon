package com.beacon.ingest.usafed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "stateful.mongo.enabled=false")
@Import(TestBeansConfiguration.class)
class IngestUsaFedApplicationTests {

    @Test
    void contextLoads() {
    }
}
