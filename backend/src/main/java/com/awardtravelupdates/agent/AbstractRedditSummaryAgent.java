package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.util.List;

public abstract class AbstractRedditSummaryAgent extends AbstractSummaryAgent {

    public AbstractRedditSummaryAgent(RestClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    public abstract String getSubreddit();

    public abstract List<SummaryUpdate> summarize(List<RedditPost> posts);
}
