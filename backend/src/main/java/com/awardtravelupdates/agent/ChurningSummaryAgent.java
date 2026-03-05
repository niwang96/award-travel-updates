package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.RedditComment;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ChurningSummaryAgent extends AbstractSummaryAgent {

    private static final int POSTS_DAYS_LIMIT = 3;

    private static final String SYSTEM_PROMPT =
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New transfer partners added by banks, " +
            "(2) Active transfer bonuses (include the bonus percentage and expiry if mentioned), " +
            "(3) New or limited-time card sign-up bonuses (include points amount and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes). " +
            "Skip trip reports, general questions, data points, and anything that doesn't fit these categories. " +
            "Return a JSON array of objects with \"text\" (the bullet) and \"commentIndex\" (1-based index of the comment it came from, or 0 if from the post title). " +
            "Example: [{\"text\": \"Chase added Wyndham as 1:1 transfer partner\", \"commentIndex\": 3}, {\"text\": \"Amex 30% transfer bonus to Virgin Atlantic through Mar 31\", \"commentIndex\": 7}]";

    public ChurningSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_CHURNING;
    }

    @Override
    public Mono<AgentOutput> summarize(List<RedditPost> posts) {
        Instant cutoff = Instant.now().minus(POSTS_DAYS_LIMIT, ChronoUnit.DAYS);
        List<RedditPost> filtered = posts.stream()
                .filter(p -> Instant.ofEpochSecond(p.createdUtc()).isAfter(cutoff))
                .filter(p -> p.title().toLowerCase().contains(RedditConstants.CHURNING_COMMENT_TITLE_FILTER))
                .toList();

        if (filtered.isEmpty()) {
            return Mono.just(new AgentOutput(List.of(
                    new SummaryUpdate("No news and updates posts found.", null, null))));
        }

        return Flux.fromIterable(filtered)
                .flatMap(post -> summarizePost(post))
                .collectList()
                .map(lists -> new AgentOutput(lists.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList())));
    }

    private Mono<List<SummaryUpdate>> summarizePost(RedditPost post) {
        List<RedditComment> comments = post.comments();

        String numberedComments = IntStream.range(0, comments.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + comments.get(i).body())
                .collect(Collectors.joining("\n"));

        String userMessage = "Post: " + post.title() + "\n\nComments:\n" + numberedComments;

        return callApiJson(SYSTEM_PROMPT,
                "Summarize the key deals and updates from this churning post and its comments:\n\n" + userMessage)
                .map(json -> parseUpdates(json, post, comments));
    }

    private List<SummaryUpdate> parseUpdates(JsonNode json, RedditPost post, List<RedditComment> comments) {
        List<SummaryUpdate> updates = new ArrayList<>();
        for (JsonNode item : json) {
            String text = item.path("text").asText();
            int commentIndex = item.path("commentIndex").asInt(0);
            String source = resolveSource(commentIndex, post, comments);
            long timestamp = resolveTimestamp(commentIndex, post, comments);
            updates.add(new SummaryUpdate(text, source, timestamp));
        }
        return updates;
    }

    private Optional<RedditComment> resolveComment(int commentIndex, List<RedditComment> comments) {
        if (commentIndex > 0 && commentIndex <= comments.size()) {
            return Optional.of(comments.get(commentIndex - 1));
        }
        return Optional.empty();
    }

    private String resolveSource(int commentIndex, RedditPost post, List<RedditComment> comments) {
        return resolveComment(commentIndex, comments)
                .map(RedditComment::permalink)
                .orElse(post.permalink());
    }

    private long resolveTimestamp(int commentIndex, RedditPost post, List<RedditComment> comments) {
        return resolveComment(commentIndex, comments)
                .map(RedditComment::createdUtc)
                .orElse(post.createdUtc());
    }
}
