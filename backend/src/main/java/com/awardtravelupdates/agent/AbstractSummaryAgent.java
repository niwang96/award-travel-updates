package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class AbstractSummaryAgent extends AbstractLlmAgent {

    public AbstractSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    public abstract String getSubreddit();

    public abstract Mono<AgentOutput> summarize(List<RedditPost> posts);
}
