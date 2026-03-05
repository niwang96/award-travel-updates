package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditPost;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AbstractSummaryAgent {

    String getSubreddit();

    Mono<AgentOutput> summarize(List<RedditPost> posts);
}
