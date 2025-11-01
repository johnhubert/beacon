package com.beacon.ingest.usafed.service;

import com.beacon.ingest.usafed.llm.LlmClient;
import com.beacon.ingest.usafed.llm.Model;
import com.beacon.ingest.usafed.llm.OpenAiModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Generates concise legislation summaries by scraping source content and delegating the synthesis to an LLM.
 */
@Service
public class LegislationSummaryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegislationSummaryService.class);
    private static final int MAX_CONTENT_LENGTH = 16_000;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final LlmClient llmClient;
    private final HttpClient httpClient;
    private final Model summaryModel;

    @Autowired
    public LegislationSummaryService(LlmClient llmClient) {
        this(llmClient, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(FETCH_TIMEOUT)
                .build(),
                OpenAiModel.GPT_4O_MINI);
    }

    LegislationSummaryService(LlmClient llmClient, HttpClient httpClient, Model summaryModel) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.summaryModel = Objects.requireNonNull(summaryModel, "summaryModel");
    }

    /**
     * Scrapes the supplied legislation URL and requests a single-paragraph summary from the configured model.
     *
     * @param legislationUrl source page containing bill details
     * @return optional summary when both scraping and LLM processing succeed
     */
    public Optional<String> summarizeLegislation(String legislationUrl) {
        if (legislationUrl == null || legislationUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String documentText = fetchDocumentText(legislationUrl);
            if (documentText.isBlank()) {
                return Optional.empty();
            }
            String prompt = buildPrompt(documentText);
            String summary = llmClient.promptModel(summaryModel, prompt);
            String normalized = summary == null ? "" : summary.trim();
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (IllegalStateException ex) {
            LOGGER.warn("LLM summary generation unavailable for {}: {}", legislationUrl, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            LOGGER.error("Failed to generate legislation summary for {}", legislationUrl, ex);
            return Optional.empty();
        }
    }

    private String fetchDocumentText(String legislationUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(legislationUrl))
                .GET()
                .timeout(FETCH_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            LOGGER.warn("Unable to fetch legislation content (status {}) from {}", response.statusCode(), legislationUrl);
            return "";
        }

        String text;
        try (ByteArrayInputStream bodyStream = new ByteArrayInputStream(response.body())) {
            Document document = Jsoup.parse(bodyStream, null, legislationUrl);
            text = document == null ? "" : document.text();
        }

        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH);
        }
        return text.trim();
    }

    private String buildPrompt(String documentText) {
        return """
                Summarize the following U.S. congressional legislation into a single well-structured paragraph (maximum 120 words).
                Focus on the legislation's intent, primary actions, and notable impacts. Avoid speculation and keep the tone neutral.

                Legislation text:
                %s
                """.formatted(documentText);
    }
}
