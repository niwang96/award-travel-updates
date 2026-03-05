package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SubredditSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.agent.AbstractSummaryAgent;
import com.awardtravelupdates.repository.SummaryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SummaryService {

    private static final int STALE_HOURS = 3;
    private static final int NEW_POSTS_THRESHOLD = 5;

    private final RedditService redditService;
    private final SummaryRepository summaryRepository;
    private final Map<String, AbstractSummaryAgent> agentsBySubreddit;

    public SummaryService(RedditService redditService,
                          SummaryRepository summaryRepository,
                          List<AbstractSummaryAgent> agents) {
        this.redditService = redditService;
        this.summaryRepository = summaryRepository;
        this.agentsBySubreddit = agents.stream()
                .collect(Collectors.toMap(AbstractSummaryAgent::getSubreddit, a -> a));
    }

    public Mono<Map<String, SummaryResult>> getSummaries() {
        return redditService.fetchAllPosts(RedditConstants.DEFAULT_LIMIT, null)
                .flatMap(allPosts -> {
                    Map<String, List<RedditPost>> bySubreddit = allPosts.stream()
                            .collect(Collectors.groupingBy(RedditPost::subreddit));

                    List<Mono<Map.Entry<String, SummaryResult>>> monos = agentsBySubreddit.keySet().stream()
                            .map(subreddit -> getSummary(subreddit, bySubreddit.getOrDefault(subreddit, List.of()))
                                    .map(result -> Map.entry(subreddit, result)))
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
                });
    }

    public Mono<SummaryResult> getSummary(String subreddit) {
        AbstractSummaryAgent agent = agentsBySubreddit.get(subreddit);
        if (agent == null) {
            return Mono.just(new SummaryResult(List.of("Unknown subreddit: " + subreddit), true));
        }
        return redditService.fetchAllPosts(RedditConstants.DEFAULT_LIMIT, null)
                .flatMap(allPosts -> {
                    List<RedditPost> posts = allPosts.stream()
                            .filter(p -> p.subreddit().equals(subreddit))
                            .toList();
                    return getSummary(subreddit, posts);
                });
    }

    private Mono<SummaryResult> getSummary(
            String subreddit,
            List<RedditPost> currentPosts) {
        AbstractSummaryAgent agent = agentsBySubreddit.get(subreddit);

        return Mono.fromCallable(() -> summaryRepository.findById(subreddit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalSummary -> {
                    int currentCount = currentPosts.size();
                    boolean needsRefresh = optionalSummary.isEmpty() || isStale(optionalSummary.get(), currentCount);

                    if (!needsRefresh) {
                        return Mono.just(new SummaryResult(optionalSummary.get().getBullets(), false));
                    }

                    return agent.summarize(currentPosts)
                            .flatMap(bullets -> Mono.fromCallable(() -> {
                                summaryRepository.save(new SubredditSummary(subreddit, bullets, Instant.now(), currentCount));
                                return new SummaryResult(bullets, false);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> staleFallback(optionalSummary));
                });
    }

    private Mono<SummaryResult> staleFallback(Optional<SubredditSummary> cached) {
        if (cached.isPresent()) {
            return Mono.just(new SummaryResult(cached.get().getBullets(), true));
        }
        return Mono.just(new SummaryResult(List.of("Summary unavailable — please try again later."), true));
    }

    private boolean isStale(SubredditSummary cached, int currentPostCount) {
        boolean tooOld = cached.getLastUpdated().isBefore(Instant.now().minus(STALE_HOURS, ChronoUnit.HOURS));
        boolean tooManyNewPosts = (currentPostCount - cached.getPostCount()) >= NEW_POSTS_THRESHOLD;
        return tooOld || tooManyNewPosts;
    }
}
