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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractSummaryAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final String REWARDS_ANALYST_CATEGORIES =
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New transfer partners added by banks, " +
            "(2) Active transfer bonuses (include the bonus percentage, program names, and expiry if mentioned), " +
            "(3) New, increased, or returning limited-time card sign-up bonuses (include the full card name, points/miles amount, and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes), " +
            "(6) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(7) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(8) Limited-time loyalty program promotions — bonus miles/points earned through normal loyalty activity such as specific flights, hotel stays, shopping portal purchases, or status match and challenge offers from airlines or hotels. ";

    protected static final String REWARDS_ANALYST_BULLET_STYLE =
            "Always include the primary number (e.g. bonus points/miles amount or bonus percentage) — omit any item where the key number is not mentioned. Spend requirements and expiry dates should be included when present but are not required. " +
            "Bullet style: write each bullet as a single active-voice sentence starting with the brand or program name. Include the key number. Max ~20 words. ";

    protected static final String REWARDS_ANALYST_TOPIC_ROUTING =
            "Topic assignment: categories 1-4 → credit_cards; category 5 → lounges; " +
            "categories 6-7 for airline/award programs → flights; categories 6-7 for hotel programs → hotels; " +
            "categories 6-7 for bank/credit card programs → credit_cards; " +
            "category 8: if status match or challenge → status; if airline award sale or flight-specific mileage promotion (e.g. bonus miles for flying a route) → flights; all other category 8 → deals. ";

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
        Set<Integer> seenPostIndexes = new HashSet<>();
        for (JsonNode item : json) {
            int postIndex = item.path("postIndex").asInt(0);
            if (isValidPostIndex(postIndex, posts.size()) && seenPostIndexes.add(postIndex)) {
                updates.add(toUpdate.apply(item, posts.get(postIndex - 1)));
            }
        }
        return updates;
    }

    private boolean isValidPostIndex(int postIndex, int postCount) {
        return postIndex >= 1 && postIndex <= postCount;
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
