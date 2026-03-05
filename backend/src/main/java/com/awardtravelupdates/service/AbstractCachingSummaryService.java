package com.awardtravelupdates.service;

import com.awardtravelupdates.model.AbstractCachedSummary;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractCachingSummaryService<POST, CACHED extends AbstractCachedSummary> {

    public Mono<Map<String, SummaryResult>> getSummaries() {
        List<Mono<Map.Entry<String, SummaryResult>>> monos = getIds().stream()
                .map(id -> fetchPosts(id)
                        .flatMap(posts -> computeSummary(id, posts))
                        .map(result -> Map.entry(id, result)))
                .toList();

        return Mono.zip(monos, results -> {
            Map<String, SummaryResult> map = new HashMap<>();
            for (Object r : results) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, SummaryResult> entry = (Map.Entry<String, SummaryResult>) r;
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        });
    }

    public Mono<SummaryResult> getSummary(String id) {
        if (!hasAgent(id)) {
            return Mono.just(unknownIdResult(id));
        }
        return fetchPosts(id).flatMap(posts -> computeSummary(id, posts));
    }

    private Mono<SummaryResult> computeSummary(String id, List<POST> posts) {
        return Mono.fromCallable(() -> findCached(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalCached -> {
                    int currentCount = posts.size();
                    boolean needsRefresh = optionalCached.isEmpty() || isStale(optionalCached.get(), currentCount);

                    if (!needsRefresh) {
                        return Mono.just(new SummaryResult(optionalCached.get().getUpdates(), false));
                    }

                    return summarize(id, posts)
                            .flatMap(output -> Mono.fromCallable(() -> {
                                saveToCache(id, output.updates(), currentCount);
                                return new SummaryResult(output.updates(), false);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> buildFallbackResult(optionalCached));
                });
    }

    private Mono<SummaryResult> buildFallbackResult(Optional<CACHED> cached) {
        if (cached.isPresent()) {
            return Mono.just(new SummaryResult(cached.get().getUpdates(), true));
        }
        return Mono.just(new SummaryResult(
                List.of(new SummaryUpdate("Summary unavailable — please try again later.", null, null)), true));
    }

    protected abstract Set<String> getIds();

    protected abstract boolean hasAgent(String id);

    protected abstract Mono<List<POST>> fetchPosts(String id);

    protected abstract Mono<AgentOutput> summarize(String id, List<POST> posts);

    protected abstract Optional<CACHED> findCached(String id);

    protected abstract void saveToCache(String id, List<SummaryUpdate> updates, int count);

    protected abstract boolean isStale(CACHED cached, int currentCount);

    protected abstract SummaryResult unknownIdResult(String id);
}
