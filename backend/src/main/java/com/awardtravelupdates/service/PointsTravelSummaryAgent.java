package com.awardtravelupdates.service;

import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PointsTravelSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are a travel rewards deal hunter. Only include concrete, actionable deal alerts for award tickets. " +
            "Each bullet must specify: airline, route (origin-destination), cost in miles/points, and the loyalty program. " +
            "Skip general discussion, trip reports, questions, and anything without a specific route and mileage cost. " +
            "If there are no clear deal alerts, return an empty array. " +
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"Singapore Airlines Business JFK-FRA: 67k KrisFlyer miles\", \"ANA First NRT-JFK: 110k Aeroplan miles\"]";

    private final WebClient geminiClient;
    private final ObjectMapper objectMapper;

    public PointsTravelSummaryAgent(WebClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public Mono<List<String>> summarize(List<RedditPost> posts) {
        if (posts.isEmpty()) {
            return Mono.just(List.of("No pointstravel posts found."));
        }

        String postsText = posts.stream()
                .map(post -> "Title: " + post.title() + "\n" +
                        (post.selftext().isBlank() ? "" : "Body: " + post.selftext()))
                .collect(Collectors.joining("\n\n---\n\n"));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("parts", List.of(Map.of(
                        "text", "Summarize the key deals and updates from these pointstravel posts:\n\n" + postsText
                ))))
        );

        return geminiClient.post()
                .uri("/v1beta/models/gemini-2.5-flash:generateContent")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseResponse)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(e -> e instanceof WebClientResponseException.TooManyRequests))
                .onErrorReturn(List.of("Error generating summary."));
    }

    private List<String> parseResponse(JsonNode response) {
        String text = response.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
        text = stripMarkdownFences(text);
        try {
            JsonNode arr = objectMapper.readTree(text);
            return objectMapper.convertValue(arr, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of(text);
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
