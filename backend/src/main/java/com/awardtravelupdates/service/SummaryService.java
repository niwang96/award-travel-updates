package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SubredditSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.repository.SummaryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final AwardTravelSummaryAgent awardTravelAgent;
    private final ChurningSummaryAgent churningAgent;
    private final PointsTravelSummaryAgent pointsTravelAgent;

    public SummaryService(RedditService redditService,
                          SummaryRepository summaryRepository,
                          AwardTravelSummaryAgent awardTravelAgent,
                          ChurningSummaryAgent churningAgent,
                          PointsTravelSummaryAgent pointsTravelAgent) {
        this.redditService = redditService;
        this.summaryRepository = summaryRepository;
        this.awardTravelAgent = awardTravelAgent;
        this.churningAgent = churningAgent;
        this.pointsTravelAgent = pointsTravelAgent;
    }

    public Mono<Map<String, SummaryResult>> getSummaries() {
        return redditService.fetchAllPosts(RedditConstants.DEFAULT_LIMIT, null)
                .flatMap(allPosts -> {
                    Map<String, List<RedditPost>> bySubreddit = allPosts.stream()
                            .collect(Collectors.groupingBy(RedditPost::subreddit));

                    Mono<SummaryResult> awardTravelMono = getSummaryForSubreddit(
                            RedditConstants.SUBREDDIT_AWARD_TRAVEL,
                            bySubreddit.getOrDefault(RedditConstants.SUBREDDIT_AWARD_TRAVEL, List.of()),
                            awardTravelAgent::summarize);

                    Mono<SummaryResult> churningMono = getSummaryForSubreddit(
                            RedditConstants.SUBREDDIT_CHURNING,
                            bySubreddit.getOrDefault(RedditConstants.SUBREDDIT_CHURNING, List.of()),
                            churningAgent::summarize);

                    Mono<SummaryResult> pointsTravelMono = getSummaryForSubreddit(
                            RedditConstants.SUBREDDIT_POINTS_TRAVEL,
                            bySubreddit.getOrDefault(RedditConstants.SUBREDDIT_POINTS_TRAVEL, List.of()),
                            pointsTravelAgent::summarize);

                    return Mono.zip(awardTravelMono, churningMono, pointsTravelMono)
                            .map(tuple -> Map.of(
                                    RedditConstants.SUBREDDIT_AWARD_TRAVEL, tuple.getT1(),
                                    RedditConstants.SUBREDDIT_CHURNING, tuple.getT2(),
                                    RedditConstants.SUBREDDIT_POINTS_TRAVEL, tuple.getT3()
                            ));
                });
    }

    private Mono<SummaryResult> getSummaryForSubreddit(
            String subreddit,
            List<RedditPost> currentPosts,
            java.util.function.Function<List<RedditPost>, Mono<List<String>>> agentFn) {

        return Mono.fromCallable(() -> summaryRepository.findById(subreddit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalSummary -> {
                    int currentCount = currentPosts.size();
                    boolean needsRefresh = optionalSummary.isEmpty() || isStale(optionalSummary.get(), currentCount);

                    if (!needsRefresh) {
                        return Mono.just(new SummaryResult(optionalSummary.get().getBullets(), false));
                    }

                    return agentFn.apply(currentPosts)
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
