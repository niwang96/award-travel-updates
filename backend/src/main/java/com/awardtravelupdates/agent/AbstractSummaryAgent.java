package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public abstract class AbstractSummaryAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractSummaryAgent.class);

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestClient groqClient;
    private final ObjectMapper objectMapper;

    protected static AgentOutput fallbackOutput(String message) {
        return new AgentOutput(List.of(new SummaryUpdate(message, null, null)));
    }

    protected JsonNode callApiJson(String systemPrompt, String userMessage) {
        return callWithRetry(
                () -> {
                    JsonNode response = postToGroq(systemPrompt, userMessage);
                    return parseJson(extractText(response));
                },
                objectMapper.createArrayNode());
    }

    private JsonNode postToGroq(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        return groqClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private <T> T callWithRetry(java.util.function.Supplier<T> call, T fallback) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
                    log.warn("Groq rate limit hit (attempt {}/{}), retrying after backoff", attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (1L << attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Groq API error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                    break;
                }
            } catch (Exception e) {
                log.error("Groq call failed: {}", e.getMessage());
                break;
            }
        }
        return fallback;
    }

    private String extractText(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("Unexpected Groq response structure — missing choices: {}", response);
            return "[]";
        }
        return choices.get(0).path("message").path("content").asText();
    }

    private JsonNode parseJson(String text) {
        text = stripMarkdownFences(text);
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            log.error("Failed to parse Groq response as JSON: {}", text);
            return objectMapper.createArrayNode();
        }
    }

    private String stripMarkdownFences(String text) {
        String stripped = text.trim();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline != -1) stripped = stripped.substring(firstNewline + 1);
            if (stripped.endsWith("```")) stripped = stripped.substring(0, stripped.lastIndexOf("```")).trim();
        }
        return stripped;
    }
}
