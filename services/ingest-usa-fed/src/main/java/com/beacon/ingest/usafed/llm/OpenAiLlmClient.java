package com.beacon.ingest.usafed.llm;

import com.beacon.ingest.usafed.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI-backed implementation of {@link LlmClient} that leverages the Chat Completions API.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;
    private final URI chatCompletionsUri;

    public OpenAiLlmClient(HttpClient httpClient, ObjectMapper objectMapper, OpenAiProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.chatCompletionsUri = resolveChatCompletionsUri(properties.baseUrl());
    }

    @Override
    public String promptModel(Model model, String prompt) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        String sanitizedPrompt = prompt == null ? "" : prompt.trim();
        if (sanitizedPrompt.isEmpty()) {
            LOGGER.debug("Skipping OpenAI request for model {} because prompt is empty", model.identifier());
            return "";
        }
        if (properties.apiKey().isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model.identifier());

        // Construct the minimal chat payload (single user message) so default parameters apply automatically.
        ArrayNode messages = payload.putArray("messages");
        ObjectNode messageNode = messages.addObject();
        messageNode.put("role", "user");
        messageNode.put("content", sanitizedPrompt);

        HttpRequest request = buildRequest(payload);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                LOGGER.warn(
                        "OpenAI responded with status {} for model {} at {}",
                        response.statusCode(),
                        model.identifier(),
                        chatCompletionsUri);
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode());
            }
            return extractContent(response.body(), model);
        } catch (IOException ex) {
            LOGGER.error("I/O failure while invoking OpenAI for model {}", model.identifier(), ex);
            throw new IllegalStateException("OpenAI request failed due to I/O error", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.error("OpenAI request interrupted for model {}", model.identifier(), ex);
            throw new IllegalStateException("OpenAI request interrupted", ex);
        }
    }

    private HttpRequest buildRequest(ObjectNode payload) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        if (!properties.organization().isBlank()) {
            builder.header("OpenAI-Organization", properties.organization());
        }
        return builder.build();
    }

    private String extractContent(String responseBody, Model model) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            LOGGER.warn("OpenAI response for model {} did not contain choices", model.identifier());
            return "";
        }
        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        if (!message.isObject()) {
            LOGGER.warn("OpenAI response for model {} did not include message content", model.identifier());
            return "";
        }
        String content = message.path("content").asText("");
        return content == null ? "" : content.trim();
    }

    private URI resolveChatCompletionsUri(URI baseUri) {
        String base = Objects.requireNonNullElse(baseUri, URI.create("https://api.openai.com/v1")).toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + CHAT_COMPLETIONS_PATH);
    }
}
