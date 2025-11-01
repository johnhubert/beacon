package com.beacon.ingest.usafed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.beacon.ingest.usafed.llm.LlmClient;
import com.beacon.ingest.usafed.llm.OpenAiModel;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LegislationSummaryServiceTest {

    private LlmClient llmClient;
    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> httpResponse = (HttpResponse<byte[]>) Mockito.mock(HttpResponse.class);
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = Mockito.mock(LlmClient.class);
        httpClient = Mockito.mock(HttpClient.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("<html><body><p>Legislation text content</p></body></html>"
                .getBytes(StandardCharsets.UTF_8));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(llmClient.promptModel(any(), any())).thenReturn("Concise summary");
    }

    @Test
    void summarizeLegislationReturnsEmptyWhenUrlMissing() {
        LegislationSummaryService service = new LegislationSummaryService(llmClient, httpClient, OpenAiModel.GPT_4O_MINI);

        Optional<String> result = service.summarizeLegislation("");

        assertTrue(result.isEmpty(), "Expected empty result when legislation URL missing");
        verifyNoInteractions(httpClient);
        verify(llmClient, never()).promptModel(any(), any());
    }

    @Test
    void summarizeLegislationReturnsSummaryWhenFetchSucceeds() {
        LegislationSummaryService service = new LegislationSummaryService(llmClient, httpClient, OpenAiModel.GPT_4O_MINI);

        Optional<String> result = service.summarizeLegislation("https://example.com/bill");

        assertEquals(Optional.of("Concise summary"), result);
        try {
            verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception ex) {
            Assertions.fail(ex);
        }
        verify(llmClient).promptModel(any(), Mockito.contains("Legislation text content"));
    }
}
