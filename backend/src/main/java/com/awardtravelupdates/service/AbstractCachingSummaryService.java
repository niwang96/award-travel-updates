package com.awardtravelupdates.service;

import com.awardtravelupdates.model.AbstractCachedSummary;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public abstract class AbstractCachingSummaryService<POST, CACHED extends AbstractCachedSummary> {

    public Map<String, SummaryResult> getSummaries() {
        // Fetch posts for all sources in parallel (pure network I/O, no Groq calls)
        Map<String, List<POST>> allPosts;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map.Entry<String, List<POST>>>> futures = getIds().stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> Map.entry(id, fetchPosts(id)), executor))
                    .toList();
            allPosts = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        // Summarize sequentially to avoid hitting Groq rate limits
        return getIds().stream()
                .collect(Collectors.toMap(id -> id, id -> computeSummary(id, allPosts.get(id))));
    }

    public SummaryResult getSummary(String id) {
        if (!hasAgent(id)) {
            return unknownIdResult(id);
        }
        List<POST> posts = fetchPosts(id);
        return computeSummary(id, posts);
    }

    private SummaryResult computeSummary(String id, List<POST> posts) {
        Optional<CACHED> cached = findCached(id);
        int currentCount = posts.size();
        boolean needsRefresh = cached.isEmpty() || isStale(cached.get(), currentCount);

        if (!needsRefresh) {
            return new SummaryResult(cached.get().getUpdates(), false);
        }

        try {
            AgentOutput output = summarize(id, posts);
            saveToCache(id, output.updates(), currentCount);
            return new SummaryResult(output.updates(), false);
        } catch (Exception e) {
            return buildFallbackResult(cached);
        }
    }

    private SummaryResult buildFallbackResult(Optional<CACHED> cached) {
        if (cached.isPresent()) {
            return new SummaryResult(cached.get().getUpdates(), true);
        }
        return new SummaryResult(
                List.of(new SummaryUpdate("Summary unavailable — please try again later.", null, null)), true);
    }

    protected abstract Set<String> getIds();

    protected abstract boolean hasAgent(String id);

    protected abstract List<POST> fetchPosts(String id);

    protected abstract AgentOutput summarize(String id, List<POST> posts);

    protected abstract Optional<CACHED> findCached(String id);

    protected abstract void saveToCache(String id, List<SummaryUpdate> updates, int count);

    protected abstract boolean isStale(CACHED cached, int currentCount);

    protected abstract SummaryResult unknownIdResult(String id);
}
