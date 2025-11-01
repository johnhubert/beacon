package com.beacon.ingest.usafed.llm;

/**
 * Represents a large language model that can be invoked by the {@link LlmClient}. Implementations provide
 * provider-specific identifiers while remaining opaque to client code.
 */
public interface Model {

    /**
     * Returns the provider-specific identifier used when issuing requests.
     *
     * @return non-empty model identifier
     */
    String identifier();
}
