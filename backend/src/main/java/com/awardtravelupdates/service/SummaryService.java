package com.awardtravelupdates.service;

import com.awardtravelupdates.agent.AbstractSummaryAgent;
import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SubredditSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
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
        List<Mono<Map.Entry<String, SummaryResult>>> monos = agentsBySubreddit.keySet().stream()
                .map(subreddit -> redditService.fetchPostsForSubreddit(subreddit)
                        .flatMap(posts -> getSummary(subreddit, posts))
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
    }

    public Mono<SummaryResult> getSummary(String subreddit) {
        AbstractSummaryAgent agent = agentsBySubreddit.get(subreddit);
        if (agent == null) {
            return Mono.just(new SummaryResult(List.of(new SummaryUpdate("Unknown subreddit: " + subreddit, null, null)), true));
        }
        return redditService.fetchPostsForSubreddit(subreddit)
                .flatMap(posts -> getSummary(subreddit, posts));
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
                        return Mono.just(new SummaryResult(optionalSummary.get().getUpdates(), false));
                    }

                    return agent.summarize(currentPosts)
                            .flatMap(output -> Mono.fromCallable(() -> {
                                summaryRepository.save(new SubredditSummary(subreddit, output.updates(), Instant.now(), currentCount));
                                return new SummaryResult(output.updates(), false);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> staleFallback(optionalSummary));
                });
    }

    private Mono<SummaryResult> staleFallback(Optional<SubredditSummary> cached) {
        if (cached.isPresent()) {
            return Mono.just(new SummaryResult(cached.get().getUpdates(), true));
        }
        return Mono.just(new SummaryResult(
                List.of(new SummaryUpdate("Summary unavailable — please try again later.", null, null)), true));
    }

    private boolean isStale(SubredditSummary cached, int currentPostCount) {
        boolean tooOld = cached.getLastUpdated().isBefore(Instant.now().minus(RedditConstants.STALE_HOURS, ChronoUnit.HOURS));
        boolean tooManyNewPosts = (currentPostCount - cached.getPostCount()) >= RedditConstants.NEW_POSTS_THRESHOLD;
        return tooOld || tooManyNewPosts;
    }
}
