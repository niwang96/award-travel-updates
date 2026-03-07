package com.awardtravelupdates.accessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroqAccessor {

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestClient groqClient;
    private final ObjectMapper objectMapper;

    public JsonNode callForJson(String systemPrompt, String userMessage) {
        return callWithRetry(
                () -> parseJson(extractContent(postToGroq(systemPrompt, userMessage))),
                objectMapper.createArrayNode());
    }

    private JsonNode postToGroq(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 2048,
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

    private String extractContent(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("Unexpected Groq response structure — missing choices: " + response);
        }
        return choices.get(0).path("message").path("content").asText();
    }

    private JsonNode parseJson(String text) {
        String stripped = extractJsonFragment(text);
        try {
            return objectMapper.readTree(stripped);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Groq response as JSON: " + stripped, e);
        }
    }

    private String extractJsonFragment(String text) {
        String stripped = text.trim();
        // Strip markdown code fences
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline != -1) stripped = stripped.substring(firstNewline + 1);
            if (stripped.endsWith("```")) stripped = stripped.substring(0, stripped.lastIndexOf("```")).trim();
        }
        // Skip any prose preamble before the JSON starts
        int arrayStart = stripped.indexOf('[');
        int objectStart = stripped.indexOf('{');
        int jsonStart = (arrayStart == -1) ? objectStart
                : (objectStart == -1) ? arrayStart
                : Math.min(arrayStart, objectStart);
        if (jsonStart > 0) {
            stripped = stripped.substring(jsonStart);
        }
        return stripped;
    }

    private <T> T callWithRetry(Supplier<T> call, T fallback) {
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
                if (attempt < MAX_RETRIES) {
                    log.warn("Groq call failed (attempt {}/{}), retrying: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                } else {
                    log.error("Groq call failed after {} attempts: {}", MAX_RETRIES + 1, e.getMessage());
                    break;
                }
            }
        }
        return fallback;
    }
}
