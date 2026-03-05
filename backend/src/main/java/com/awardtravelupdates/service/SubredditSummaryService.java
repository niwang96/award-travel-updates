package com.awardtravelupdates.service;

import com.awardtravelupdates.agent.AbstractRedditSummaryAgent;
import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.AgentOutput;
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

    private final RedditService redditService;
    private final SubredditSummaryRepository subredditSummaryRepository;
    private final Map<String, AbstractRedditSummaryAgent> agentsBySubreddit;

    public SubredditSummaryService(RedditService redditService,
                                   SubredditSummaryRepository subredditSummaryRepository,
                                   List<AbstractRedditSummaryAgent> agents) {
        this.redditService = redditService;
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
        return redditService.fetchPostsForSubreddit(id);
    }

    @Override
    protected AgentOutput summarize(String id, List<RedditPost> posts) {
        return agentsBySubreddit.get(id).summarize(posts);
    }

    @Override
    protected Optional<SubredditSummary> findCached(String id) {
        return subredditSummaryRepository.findById(id);
    }

    @Override
    protected void saveToCache(String id, List<SummaryUpdate> updates, int count) {
        subredditSummaryRepository.save(new SubredditSummary(id, updates, Instant.now(), count));
    }

    @Override
    protected boolean isStale(SubredditSummary cached, int currentCount) {
        boolean tooOld = cached.getLastUpdated().isBefore(Instant.now().minus(RedditConstants.STALE_HOURS, ChronoUnit.HOURS));
        boolean tooManyNewPosts = (currentCount - cached.getPostCount()) >= RedditConstants.NEW_POSTS_THRESHOLD;
        return tooOld || tooManyNewPosts;
    }

    @Override
    protected SummaryResult unknownIdResult(String id) {
        return new SummaryResult(List.of(new SummaryUpdate("Unknown subreddit: " + id, null, null)), true);
    }
}
