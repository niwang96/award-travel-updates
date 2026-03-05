package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwardTravelSummaryAgent extends AbstractLlmAgent implements AbstractSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are an award travel news analyst. Only extract posts that announce one of these three things: " +
            "(1) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(2) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(3) New award flight availability — an airline opening up new award inventory on a route or cabin class that was not previously available. " +
            "Disqualify any post that is: a personal trip report, a booking data point or individual redemption example, " +
            "a question or help request, speculation, or a discussion without an official announcement. " +
            "If the post does not qualify, return an empty array. " +
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"United raised Saver awards on transatlantic routes by 20%\", \"ANA opening First Class award availability on NRT-JFK to partner programs\"]";

    public AwardTravelSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_AWARD_TRAVEL;
    }

    @Override
    public Mono<AgentOutput> summarize(List<RedditPost> posts) {
        List<RedditPost> filtered = posts.stream()
                .filter(p -> p.upvotes() > 0)
                .toList();

        if (filtered.isEmpty()) {
            return Mono.just(fallbackOutput("No major award chart updates or program changes right now — check back soon."));
        }

        return Flux.fromIterable(filtered)
                .flatMap(post -> {
                    String postText = "[" + post.upvotes() + " upvotes] " + post.title() +
                            (post.selftext().isBlank() ? "" : "\n" + post.selftext());
                    return callApi(SYSTEM_PROMPT,
                            "Summarize the key news and updates from this awardtravel post:\n\n" + postText)
                            .map(bullets -> bullets.stream()
                                    .map(text -> new SummaryUpdate(text, post.permalink(), post.createdUtc()))
                                    .collect(Collectors.toList()));
                })
                .collectList()
                .map(lists -> {
                    List<SummaryUpdate> updates = lists.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    if (updates.isEmpty()) {
                        return fallbackOutput("No major award chart updates or program changes right now — check back soon.");
                    }
                    return new AgentOutput(updates);
                });
    }
}
