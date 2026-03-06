package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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

    private static final String FALLBACK_MESSAGE =
            "No major award chart updates or program changes right now — check back soon.";

    private static final int SELFTEXT_MAX_CHARS = 500;

    public RAwardTravelSummaryAgent(RestClient groqClient, ObjectMapper objectMapper,
                                    PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqClient, objectMapper, postSummaryCacheRepository);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_AWARD_TRAVEL;
    }

    @Override
    public List<SummaryUpdate> summarize(List<RedditPost> posts) {
        if (posts.isEmpty()) {
            return fallbackOutput(FALLBACK_MESSAGE);
        }

        CachePartition<RedditPost> partition = partitionByCache(posts, RedditPost::permalink);
        List<SummaryUpdate> allUpdates = new ArrayList<>(partition.cachedUpdates());

        if (!partition.uncachedPosts().isEmpty()) {
            List<SummaryUpdate> newUpdates = callLlm(partition.uncachedPosts());
            saveUpdatesBySourceUrl(partition.uncachedPosts(), RedditPost::permalink, newUpdates);
            allUpdates.addAll(newUpdates);
        }

        return allUpdates.isEmpty() ? fallbackOutput(FALLBACK_MESSAGE) : allUpdates;
    }

    private List<SummaryUpdate> callLlm(List<RedditPost> posts) {
        String numberedPosts = IntStream.range(0, posts.size())
                .mapToObj(i -> formatPost(i + 1, posts.get(i)))
                .collect(Collectors.joining("\n\n"));
        JsonNode json = callApiJson(SYSTEM_PROMPT,
                "Summarize the key news and updates from these awardtravel posts:\n\n" + numberedPosts);
        return parseUpdates(json, posts,
                (text, post) -> new SummaryUpdate(text, post.permalink(), post.createdUtc()));
    }

    private String formatPost(int index, RedditPost post) {
        String selftext = post.selftext().isBlank() ? "" : "\n" + truncate(post.selftext(), SELFTEXT_MAX_CHARS);
        return "[" + index + "] [" + post.upvotes() + " upvotes] " + post.title() + selftext;
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
