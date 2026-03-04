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
public class AwardTravelSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are an award travel expert. Highlight notable upvoted news and exclude downvoted questions. " +
            "Return a JSON array of concise bullet-point strings (no markdown fences). Example: [\"Bullet 1\", \"Bullet 2\"]";

    private final WebClient geminiClient;
    private final ObjectMapper objectMapper;

    public AwardTravelSummaryAgent(WebClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public Mono<List<String>> summarize(List<RedditPost> posts) {
        List<RedditPost> filtered = posts.stream()
                .filter(p -> p.upvotes() > 0)
                .toList();

        if (filtered.isEmpty()) {
            return Mono.just(List.of("No upvoted awardtravel posts found."));
        }

        String postsText = filtered.stream()
                .map(post -> "[" + post.upvotes() + " upvotes] " + post.title() +
                        (post.selftext().isBlank() ? "" : "\n" + post.selftext()))
                .collect(Collectors.joining("\n\n---\n\n"));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("parts", List.of(Map.of(
                        "text", "Summarize the key news and updates from these upvoted awardtravel posts:\n\n" + postsText
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
