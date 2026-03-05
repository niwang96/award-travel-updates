package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public abstract class AbstractSummaryAgent {

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestClient groqClient;
    private final ObjectMapper objectMapper;

    protected static AgentOutput fallbackOutput(String message) {
        return new AgentOutput(List.of(new SummaryUpdate(message, null, null)));
    }

    protected List<String> callApi(String systemPrompt, String userMessage) {
        return callWithRetry(
                () -> {
                    JsonNode response = postToGroq(systemPrompt, userMessage);
                    return parseStringList(extractText(response));
                },
                List.of("Error generating summary."));
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
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || attempt == MAX_RETRIES) {
                    break;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS * (1L << attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                break;
            }
        }
        return fallback;
    }

    private String extractText(JsonNode response) {
        return response.path("choices").get(0).path("message").path("content").asText();
    }

    private List<String> parseStringList(String text) {
        text = stripMarkdownFences(text);
        try {
            JsonNode arr = objectMapper.readTree(text);
            return objectMapper.convertValue(arr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of(text);
        }
    }

    private JsonNode parseJson(String text) {
        text = stripMarkdownFences(text);
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
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
