package com.awardtravelupdates.service;

import com.awardtravelupdates.accessor.RedditAccessor;
import com.awardtravelupdates.agent.AbstractRedditSummaryAgent;
import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SubredditSummary;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.SubredditSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubredditSummaryService extends AbstractCachingSummaryService<RedditPost, SubredditSummary> {

    private final RedditAccessor redditAccessor;
    private final SubredditSummaryRepository subredditSummaryRepository;
    private final Map<String, AbstractRedditSummaryAgent> agentsBySubreddit;

    public SubredditSummaryService(RedditAccessor redditAccessor,
                                   SubredditSummaryRepository subredditSummaryRepository,
                                   List<AbstractRedditSummaryAgent> agents) {
        this.redditAccessor = redditAccessor;
        this.subredditSummaryRepository = subredditSummaryRepository;
        this.agentsBySubreddit = agents.stream()
                .collect(Collectors.toMap(AbstractRedditSummaryAgent::getSubreddit, a -> a));
    }

    @Override
    protected Set<String> getIds() {
        return agentsBySubreddit.keySet();
    }

    @Override
    protected boolean hasAgent(String id) {
        return agentsBySubreddit.containsKey(id);
    }

    @Override
    protected List<RedditPost> fetchPosts(String id) {
        return redditAccessor.fetchPostsForSubreddit(id);
    }

    @Override
    protected List<SummaryUpdate> summarize(String id, List<RedditPost> posts) {
        return agentsBySubreddit.get(id).summarize(posts);
    }

    @Override
    protected Optional<SubredditSummary> findCached(String id) {
        return subredditSummaryRepository.findById(id);
    }

    @Override
    protected void saveToCache(String id, List<SummaryUpdate> updates) {
        subredditSummaryRepository.save(new SubredditSummary(id, updates, Instant.now()));
    }

    @Override
    protected boolean isStale(SubredditSummary cached) {
        return cached.getLastUpdated().isBefore(Instant.now().minus(RedditConstants.STALE_HOURS, ChronoUnit.HOURS));
    }

    @Override
    protected SummaryResult unknownIdResult(String id) {
        return new SummaryResult(List.of(new SummaryUpdate("Unknown subreddit: " + id, null, null, null)), true);
    }
}
