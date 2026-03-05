package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class RAwardTravelSummaryAgent extends AbstractRedditSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are an award travel news analyst. Only extract posts that announce one of these three things: " +
            "(1) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(2) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(3) New award flight availability — an airline opening up new award inventory on a route or cabin class that was not previously available. " +
            "Disqualify any post that is: a personal trip report, a booking data point or individual redemption example, " +
            "a question or help request, speculation, or a discussion without an official announcement. " +
            "Return a JSON array of objects with \"text\" (the bullet) and \"postIndex\" (1-based index of the post it came from). " +
            "At most one element per post. If a post has nothing newsworthy, omit it. No markdown fences. " +
            "Example: [{\"text\": \"United raised Saver awards on transatlantic routes by 20%\", \"postIndex\": 2}]";

    public RAwardTravelSummaryAgent(RestClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_AWARD_TRAVEL;
    }

    @Override
    public List<SummaryUpdate> summarize(List<RedditPost> posts) {
        if (posts.isEmpty()) {
            return fallbackOutput("No major award chart updates or program changes right now — check back soon.");
        }

        String numberedPosts = IntStream.range(0, posts.size())
                .mapToObj(i -> {
                    RedditPost post = posts.get(i);
                    String selftext = post.selftext().isBlank() ? "" : "\n" + truncate(post.selftext(), 500);
                    String text = "[" + post.upvotes() + " upvotes] " + post.title() + selftext;
                    return "[" + (i + 1) + "] " + text;
                })
                .collect(Collectors.joining("\n\n"));

        JsonNode json = callApiJson(SYSTEM_PROMPT,
                "Summarize the key news and updates from these awardtravel posts:\n\n" + numberedPosts);

        List<SummaryUpdate> updates = parseUpdates(json, posts,
                (text, post) -> new SummaryUpdate(text, post.permalink(), post.createdUtc()));
        return updates.isEmpty()
                ? fallbackOutput("No major award chart updates or program changes right now — check back soon.")
                : updates;
    }
}
