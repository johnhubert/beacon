package com.beacon.ingest.usafed.llm;

/**
 * Enumerates OpenAI chat models supported by the {@link OpenAiLlmClient}.
 */
public enum OpenAiModel implements Model {
    GPT_4O("gpt-4o"),
    GPT_4O_MINI("gpt-4o-mini"),
    GPT_4_TURBO("gpt-4-turbo"),
    GPT_3_5_TURBO("gpt-3.5-turbo-0125");

    private final String identifier;

    OpenAiModel(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public String identifier() {
        return identifier;
    }
}
