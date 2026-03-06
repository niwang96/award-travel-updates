package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.accessor.GroqAccessor;

import java.util.List;

public abstract class AbstractRedditSummaryAgent extends AbstractSummaryAgent {

    public AbstractRedditSummaryAgent(GroqAccessor groqAccessor,
                                      PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqAccessor, postSummaryCacheRepository);
    }

    public abstract String getSubreddit();

    public abstract List<SummaryUpdate> summarize(List<RedditPost> posts);
}
