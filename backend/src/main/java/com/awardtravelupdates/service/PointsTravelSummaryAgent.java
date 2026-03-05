package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PointsTravelSummaryAgent extends AbstractSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are a travel rewards deal hunter. Only include concrete, actionable deal alerts for award tickets. " +
            "Each bullet must specify: airline, route (origin-destination), cost in miles/points, and the loyalty program. " +
            "Skip general discussion, trip reports, questions, and anything without a specific route and mileage cost. " +
            "If there are no clear deal alerts, return an empty array. " +
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"Singapore Airlines Business JFK-FRA: 67k KrisFlyer miles\", \"ANA First NRT-JFK: 110k Aeroplan miles\"]";

    public PointsTravelSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_POINTS_TRAVEL;
    }

    @Override
    public Mono<List<String>> summarize(List<RedditPost> posts) {
        if (posts.isEmpty()) {
            return Mono.just(List.of("No pointstravel posts found."));
        }

        String postsText = posts.stream()
                .map(post -> "Title: " + post.title() + "\n" +
                        (post.selftext().isBlank() ? "" : "Body: " + post.selftext()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return callApi(SYSTEM_PROMPT,
                "Summarize the key deals and updates from these pointstravel posts:\n\n" + postsText);
    }
}
