package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.PostSummaryCache;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractSummaryAgent {

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestClient groqClient;
    private final ObjectMapper objectMapper;
    private final PostSummaryCacheRepository postSummaryCacheRepository;

    protected AbstractSummaryAgent(RestClient groqClient, ObjectMapper objectMapper,
                                   PostSummaryCacheRepository postSummaryCacheRepository) {
        this.groqClient = groqClient;
        this.objectMapper = objectMapper;
        this.postSummaryCacheRepository = postSummaryCacheRepository;
    }

    protected record CachePartition<P>(List<SummaryUpdate> cachedUpdates, List<P> uncachedPosts) {}

    /**
     * Splits posts into cached (with their deserialized updates) and uncached (not yet seen by the LLM)
     * using a single batch DB query.
     */
    protected <P> CachePartition<P> partitionByCache(List<P> posts, Function<P, String> urlExtractor) {
        List<String> urls = posts.stream().map(urlExtractor).toList();
        Map<String, PostSummaryCache> cacheByUrl = postSummaryCacheRepository.findAllById(urls)
                .stream().collect(Collectors.toMap(PostSummaryCache::getUrl, c -> c));

        List<SummaryUpdate> cachedUpdates = new ArrayList<>();
        List<P> uncachedPosts = new ArrayList<>();
        for (P post : posts) {
            PostSummaryCache entry = cacheByUrl.get(urlExtractor.apply(post));
            if (entry != null) {
                cachedUpdates.addAll(deserializeUpdates(entry.getSummaryText()));
            } else {
                uncachedPosts.add(post);
            }
        }
        return new CachePartition<>(cachedUpdates, uncachedPosts);
    }

    /**
     * Saves per-post LLM results to the cache, keyed by the post's URL.
     * Assumes SummaryUpdate.source() equals urlExtractor(post) — true for blog and awardtravel agents.
     * Posts with no matching update are saved as null (= "processed, not relevant").
     */
    protected <P> void saveUpdatesBySourceUrl(List<P> uncachedPosts, Function<P, String> urlExtractor,
                                              List<SummaryUpdate> newUpdates) {
        Map<String, List<SummaryUpdate>> updatesByUrl = newUpdates.stream()
                .collect(Collectors.groupingBy(SummaryUpdate::source));
        for (P post : uncachedPosts) {
            String url = urlExtractor.apply(post);
            savePostCache(url, updatesByUrl.getOrDefault(url, List.of()));
        }
    }

    protected void savePostCache(String url, List<SummaryUpdate> updates) {
        String json = updates.isEmpty() ? null : serializeUpdates(updates);
        postSummaryCacheRepository.save(new PostSummaryCache(url, json, Instant.now()));
    }

    protected static List<SummaryUpdate> fallbackOutput(String message) {
        return List.of(new SummaryUpdate(message, null, null));
    }

    protected JsonNode callApiJson(String systemPrompt, String userMessage) {
        return callWithRetry(
                () -> parseJson(extractContent(postToGroq(systemPrompt, userMessage))),
                objectMapper.createArrayNode());
    }

    protected <P> List<SummaryUpdate> parseUpdates(JsonNode json, List<P> posts,
                                                   BiFunction<String, P, SummaryUpdate> toUpdate) {
        List<SummaryUpdate> updates = new ArrayList<>();
        for (JsonNode item : json) {
            int postIndex = item.path("postIndex").asInt(0);
            if (postIndex >= 1 && postIndex <= posts.size()) {
                updates.add(toUpdate.apply(item.path("text").asText(), posts.get(postIndex - 1)));
            }
        }
        return updates;
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

    private String extractContent(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("Unexpected Groq response structure — missing choices: {}", response);
            return "[]";
        }
        return choices.get(0).path("message").path("content").asText();
    }

    private JsonNode parseJson(String text) {
        String stripped = stripMarkdownFences(text);
        try {
            return objectMapper.readTree(stripped);
        } catch (Exception e) {
            log.error("Failed to parse Groq response as JSON: {}", stripped);
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
                log.error("Groq call failed: {}", e.getMessage());
                break;
            }
        }
        return fallback;
    }

    private String serializeUpdates(List<SummaryUpdate> updates) {
        try {
            return objectMapper.writeValueAsString(updates);
        } catch (Exception e) {
            log.error("Failed to serialize updates for cache: {}", e.getMessage());
            return null;
        }
    }

    private List<SummaryUpdate> deserializeUpdates(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<SummaryUpdate>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize cached updates: {}", e.getMessage());
            return List.of();
        }
    }
}
