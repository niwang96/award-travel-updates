package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.PostSummaryCache;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.accessor.GroqAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractSummaryAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GroqAccessor groqAccessor;
    private final PostSummaryCacheRepository postSummaryCacheRepository;

    protected AbstractSummaryAgent(GroqAccessor groqAccessor,
                                   PostSummaryCacheRepository postSummaryCacheRepository) {
        this.groqAccessor = groqAccessor;
        this.postSummaryCacheRepository = postSummaryCacheRepository;
    }

    private static final int POST_CACHE_TTL_HOURS = 3;

    protected record CachePartition<P>(List<SummaryUpdate> cachedUpdates, List<P> uncachedPosts) {}

    /**
     * Splits posts into cached (with their deserialized updates) and uncached (not yet seen by the LLM).
     * Cache entries older than POST_CACHE_TTL_HOURS are treated as uncached and re-evaluated.
     */
    protected <P> CachePartition<P> partitionByCache(List<P> posts, Function<P, String> urlExtractor) {
        List<String> urls = posts.stream().map(urlExtractor).toList();
        Map<String, PostSummaryCache> cacheByUrl = postSummaryCacheRepository.findAllById(urls)
                .stream().collect(Collectors.toMap(PostSummaryCache::getUrl, c -> c));

        Instant ttlCutoff = Instant.now().minus(POST_CACHE_TTL_HOURS, ChronoUnit.HOURS);
        List<SummaryUpdate> cachedUpdates = new ArrayList<>();
        List<P> uncachedPosts = new ArrayList<>();
        for (P post : posts) {
            PostSummaryCache entry = cacheByUrl.get(urlExtractor.apply(post));
            if (entry != null && entry.getProcessedAt().isAfter(ttlCutoff)) {
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
        return List.of(new SummaryUpdate(message, null, null, null));
    }

    protected JsonNode callApiJson(String systemPrompt, String userMessage) {
        return groqAccessor.callForJson(systemPrompt, userMessage);
    }

    protected <P> List<SummaryUpdate> parseUpdates(JsonNode json, List<P> posts,
                                                   BiFunction<JsonNode, P, SummaryUpdate> toUpdate) {
        List<SummaryUpdate> updates = new ArrayList<>();
        for (JsonNode item : json) {
            int postIndex = item.path("postIndex").asInt(0);
            if (postIndex >= 1 && postIndex <= posts.size()) {
                updates.add(toUpdate.apply(item, posts.get(postIndex - 1)));
            }
        }
        return updates;
    }

    private String serializeUpdates(List<SummaryUpdate> updates) {
        try {
            return OBJECT_MAPPER.writeValueAsString(updates);
        } catch (Exception e) {
            log.error("Failed to serialize updates for cache: {}", e.getMessage());
            return null;
        }
    }

    private List<SummaryUpdate> deserializeUpdates(String json) {
        if (json == null) return List.of();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<SummaryUpdate>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize cached updates: {}", e.getMessage());
            return List.of();
        }
    }
}
