package com.awardtravelupdates.service;

import com.awardtravelupdates.agent.AbstractBlogSummaryAgent;
import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.model.BlogPost;
import com.awardtravelupdates.model.BlogSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.BlogSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogSummaryService extends AbstractCachingSummaryService<BlogPost, BlogSummary> {

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

    @Override
    protected Set<String> getIds() {
        return agentsByBlogId.keySet();
    }

    @Override
    protected boolean hasAgent(String id) {
        return agentsByBlogId.containsKey(id);
    }

    @Override
    protected List<BlogPost> fetchPosts(String id) {
        return blogService.fetchPostsForBlog(id);
    }

    @Override
    protected List<SummaryUpdate> summarize(String id, List<BlogPost> posts) {
        return agentsByBlogId.get(id).summarize(posts);
    }

    @Override
    protected Optional<BlogSummary> findCached(String id) {
        return blogSummaryRepository.findById(id);
    }

    @Override
    protected void saveToCache(String id, List<SummaryUpdate> updates) {
        blogSummaryRepository.save(new BlogSummary(id, updates, Instant.now()));
    }

    @Override
    protected boolean isStale(BlogSummary cached) {
        return cached.getLastUpdated().isBefore(Instant.now().minus(BlogConstants.BLOG_STALE_HOURS, ChronoUnit.HOURS));
    }

    @Override
    protected SummaryResult unknownIdResult(String id) {
        return new SummaryResult(List.of(new SummaryUpdate("Unknown blog: " + id, null, null)), true);
    }
}
