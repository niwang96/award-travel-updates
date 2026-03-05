package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditComment;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class RChurningSummaryAgent extends AbstractRedditSummaryAgent {

    private static final int POSTS_DAYS_LIMIT = 3;

    private static final String SYSTEM_PROMPT =
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New transfer partners added by banks, " +
            "(2) Active transfer bonuses (include the bonus percentage and expiry if mentioned), " +
            "(3) New or limited-time card sign-up bonuses (include the full card name, points amount, and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes), " +
            "(6) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(7) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(8) Limited-time loyalty program promotions — bonus miles/points for specific flights, hotel stays, or activities, and status match or challenge offers from airlines or hotels. " +
            "Skip trip reports, general questions, data points, and anything that doesn't fit these categories. " +
            "Return a JSON array of objects with \"text\" (the bullet), \"postIndex\" (1-based index of the post), " +
            "and \"commentIndex\" (1-based index of the comment within that post, or 0 if from the post title). " +
            "Example: [{\"text\": \"Chase added Wyndham as 1:1 transfer partner\", \"postIndex\": 1, \"commentIndex\": 3}]";

    public RChurningSummaryAgent(RestClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_CHURNING;
    }

    @Override
    public AgentOutput summarize(List<RedditPost> posts) {
        Instant cutoff = Instant.now().minus(POSTS_DAYS_LIMIT, ChronoUnit.DAYS);
        List<RedditPost> filtered = posts.stream()
                .filter(p -> Instant.ofEpochSecond(p.createdUtc()).isAfter(cutoff))
                .filter(p -> p.title().toLowerCase().contains(RedditConstants.CHURNING_COMMENT_TITLE_FILTER))
                .toList();

        if (filtered.isEmpty()) {
            return fallbackOutput("No news and updates posts found.");
        }

        String numberedPosts = IntStream.range(0, filtered.size())
                .mapToObj(i -> {
                    RedditPost post = filtered.get(i);
                    String numberedComments = IntStream.range(0, post.comments().size())
                            .mapToObj(j -> "  [" + (j + 1) + "] " + post.comments().get(j).body())
                            .collect(Collectors.joining("\n"));
                    return "Post " + (i + 1) + ": " + post.title() + "\n" + numberedComments;
                })
                .collect(Collectors.joining("\n\n"));

        JsonNode json = callApiJson(SYSTEM_PROMPT,
                "Summarize the key deals and updates from these churning posts and their comments:\n\n" + numberedPosts);

        List<SummaryUpdate> updates = parseUpdates(json, filtered);
        return new AgentOutput(updates);
    }

    private List<SummaryUpdate> parseUpdates(JsonNode json, List<RedditPost> posts) {
        List<SummaryUpdate> updates = new ArrayList<>();
        for (JsonNode item : json) {
            String text = item.path("text").asText();
            int postIndex = item.path("postIndex").asInt(0);
            int commentIndex = item.path("commentIndex").asInt(0);

            if (postIndex >= 1 && postIndex <= posts.size()) {
                RedditPost post = posts.get(postIndex - 1);
                String source = resolveSource(commentIndex, post);
                long timestamp = resolveTimestamp(commentIndex, post);
                updates.add(new SummaryUpdate(text, source, timestamp));
            }
        }
        return updates;
    }

    private Optional<RedditComment> resolveComment(int commentIndex, RedditPost post) {
        List<RedditComment> comments = post.comments();
        if (commentIndex > 0 && commentIndex <= comments.size()) {
            return Optional.of(comments.get(commentIndex - 1));
        }
        return Optional.empty();
    }

    private String resolveSource(int commentIndex, RedditPost post) {
        return resolveComment(commentIndex, post)
                .map(RedditComment::permalink)
                .orElse(post.permalink());
    }

    private long resolveTimestamp(int commentIndex, RedditPost post) {
        return resolveComment(commentIndex, post)
                .map(RedditComment::createdUtc)
                .orElse(post.createdUtc());
    }
}
