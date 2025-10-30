package com.beacon.rest.officials;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REST service that exposes public official profiles to the
 * UI.
 */
@SpringBootApplication
public class RestOfficialsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestOfficialsApplication.class, args);
    }
}
