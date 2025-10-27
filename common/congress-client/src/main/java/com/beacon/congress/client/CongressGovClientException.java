package com.beacon.congress.client;

/**
 * Runtime exception thrown when the Congress.gov API cannot be reached or returns an unexpected
 * payload.
 */
public class CongressGovClientException extends RuntimeException {

    public CongressGovClientException(String message) {
        super(message);
    }

    public CongressGovClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
