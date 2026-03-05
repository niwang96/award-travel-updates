package com.awardtravelupdates.service;

import com.awardtravelupdates.agent.AbstractBlogSummaryAgent;
import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.model.BlogPost;
import com.awardtravelupdates.model.BlogSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.BlogSummaryRepository;
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
public class BlogSummaryService {

    private final BlogService blogService;
    private final BlogSummaryRepository blogSummaryRepository;
    private final Map<String, AbstractBlogSummaryAgent> agentsByBlogId;

    public BlogSummaryService(BlogService blogService,
                              BlogSummaryRepository blogSummaryRepository,
                              List<AbstractBlogSummaryAgent> agents) {
        this.blogService = blogService;
        this.blogSummaryRepository = blogSummaryRepository;
        this.agentsByBlogId = agents.stream()
                .collect(Collectors.toMap(AbstractBlogSummaryAgent::getBlogId, a -> a));
    }

    public Mono<Map<String, SummaryResult>> getSummaries() {
        List<Mono<Map.Entry<String, SummaryResult>>> monos = agentsByBlogId.keySet().stream()
                .map(blogId -> blogService.fetchPostsForBlog(blogId)
                        .flatMap(posts -> getSummary(blogId, posts))
                        .map(result -> Map.entry(blogId, result)))
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

    public Mono<SummaryResult> getSummary(String blogId) {
        AbstractBlogSummaryAgent agent = agentsByBlogId.get(blogId);
        if (agent == null) {
            return Mono.just(new SummaryResult(List.of(new SummaryUpdate("Unknown blog: " + blogId, null, null)), true));
        }
        return blogService.fetchPostsForBlog(blogId)
                .flatMap(posts -> getSummary(blogId, posts));
    }

    private Mono<SummaryResult> getSummary(String blogId, List<BlogPost> currentPosts) {
        AbstractBlogSummaryAgent agent = agentsByBlogId.get(blogId);

        return Mono.fromCallable(() -> blogSummaryRepository.findById(blogId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalSummary -> {
                    int currentCount = currentPosts.size();
                    boolean needsRefresh = optionalSummary.isEmpty() || isStale(optionalSummary.get(), currentCount);

                    if (!needsRefresh) {
                        return Mono.just(new SummaryResult(optionalSummary.get().getUpdates(), false));
                    }

                    return agent.summarize(currentPosts)
                            .flatMap(output -> Mono.fromCallable(() -> {
                                blogSummaryRepository.save(new BlogSummary(blogId, output.updates(), Instant.now(), currentCount));
                                return new SummaryResult(output.updates(), false);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> staleFallback(optionalSummary));
                });
    }

    private Mono<SummaryResult> staleFallback(Optional<BlogSummary> cached) {
        if (cached.isPresent()) {
            return Mono.just(new SummaryResult(cached.get().getUpdates(), true));
        }
        return Mono.just(new SummaryResult(
                List.of(new SummaryUpdate("Summary unavailable — please try again later.", null, null)), true));
    }

    private boolean isStale(BlogSummary cached, int currentPostCount) {
        boolean tooOld = cached.getLastUpdated().isBefore(Instant.now().minus(BlogConstants.BLOG_STALE_HOURS, ChronoUnit.HOURS));
        boolean tooManyNewPosts = (currentPostCount - cached.getPostCount()) >= BlogConstants.BLOG_NEW_POSTS_THRESHOLD;
        return tooOld || tooManyNewPosts;
    }
}
