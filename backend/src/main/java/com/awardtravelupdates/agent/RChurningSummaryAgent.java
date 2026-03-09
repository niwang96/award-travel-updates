package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditComment;
import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.accessor.GroqAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class RChurningSummaryAgent extends AbstractRedditSummaryAgent {

    private static final int POSTS_DAYS_LIMIT = 3;

    private static final String SYSTEM_PROMPT =
            REWARDS_ANALYST_CATEGORIES +
            "Skip trip reports, general questions, individual data points (e.g. 'I got approved for X card last week', 'Just booked J class with 70k miles'), referral offers or referral threads, " +
            "gift cards (buying, selling, or any promotions involving gift cards), and anything that doesn't fit these categories. Only include items that announce a new or changed deal/offer available to everyone. " +
            REWARDS_ANALYST_BULLET_STYLE +
            "Return a JSON array of objects with \"text\" (the bullet), \"postIndex\" (1-based index of the post), " +
            "\"commentIndex\" (1-based index of the comment within that post, or 0 if from the post title), " +
            "and \"topic\" chosen from exactly these values: credit_cards, flights, hotels, lounges, status, deals. " +
            REWARDS_ANALYST_TOPIC_ROUTING +
            "No markdown fences. " +
            "Example: [{\"text\": \"Chase added Wyndham as a new 1:1 transfer partner\", \"postIndex\": 1, \"commentIndex\": 3, \"topic\": \"credit_cards\"}, {\"text\": \"Delta offering 500 bonus miles for flights booked to Europe through Apr 30\", \"postIndex\": 2, \"commentIndex\": 1, \"topic\": \"flights\"}]";

    public RChurningSummaryAgent(GroqAccessor groqAccessor,
                                 PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqAccessor, postSummaryCacheRepository);
    }

    @Override
    public String getSubreddit() {
        return RedditConstants.SUBREDDIT_CHURNING;
    }

    @Override
    public List<SummaryUpdate> summarize(List<RedditPost> posts) {
        List<RedditPost> recentNewsThreads = filterToRecentNewsThreads(posts);
        if (recentNewsThreads.isEmpty()) {
            return fallbackOutput("No news and updates posts found.");
        }

        CachePartition<RedditPost> partition = partitionByCache(recentNewsThreads, RedditPost::permalink);
        List<SummaryUpdate> allUpdates = new ArrayList<>(partition.cachedUpdates());

        if (!partition.uncachedPosts().isEmpty()) {
            allUpdates.addAll(callLlmAndSave(partition.uncachedPosts()));
        }

        return allUpdates;
    }

    private List<RedditPost> filterToRecentNewsThreads(List<RedditPost> posts) {
        Instant cutoff = Instant.now().minus(POSTS_DAYS_LIMIT, ChronoUnit.DAYS);
        return posts.stream()
                .filter(p -> Instant.ofEpochSecond(p.createdUtc()).isAfter(cutoff))
                .filter(p -> p.title().toLowerCase().contains(RedditConstants.CHURNING_COMMENT_TITLE_FILTER))
                .toList();
    }

    // Churning threads can produce multiple updates (one per comment), so cache is saved per-thread
    // by postIndex rather than by SummaryUpdate.source() (which is the comment permalink, not thread URL).
    private List<SummaryUpdate> callLlmAndSave(List<RedditPost> uncachedThreads) {
        JsonNode json = callApiJson(SYSTEM_PROMPT,
                "Summarize the key deals and updates from these churning posts and their comments:\n\n"
                + formatThreadsWithComments(uncachedThreads));

        Map<Integer, List<SummaryUpdate>> updatesByThreadIndex = parseUpdatesByThreadIndex(json, uncachedThreads);

        List<SummaryUpdate> newUpdates = new ArrayList<>();
        for (int i = 0; i < uncachedThreads.size(); i++) {
            List<SummaryUpdate> threadUpdates = updatesByThreadIndex.getOrDefault(i + 1, List.of());
            savePostCache(uncachedThreads.get(i).permalink(), threadUpdates);
            newUpdates.addAll(threadUpdates);
        }
        return newUpdates;
    }

    private String formatThreadsWithComments(List<RedditPost> threads) {
        return IntStream.range(0, threads.size())
                .mapToObj(i -> {
                    RedditPost thread = threads.get(i);
                    String numberedComments = IntStream.range(0, thread.comments().size())
                            .mapToObj(j -> "  [" + (j + 1) + "] " + thread.comments().get(j).body())
                            .collect(Collectors.joining("\n"));
                    return "Post " + (i + 1) + ": " + thread.title() + "\n" + numberedComments;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private Map<Integer, List<SummaryUpdate>> parseUpdatesByThreadIndex(JsonNode json, List<RedditPost> threads) {
        Map<Integer, List<SummaryUpdate>> updatesByThreadIndex = new HashMap<>();
        for (JsonNode item : json) {
            int threadIndex = item.path("postIndex").asInt(0);
            int commentIndex = item.path("commentIndex").asInt(0);
            if (threadIndex >= 1 && threadIndex <= threads.size()) {
                RedditPost thread = threads.get(threadIndex - 1);
                updatesByThreadIndex.computeIfAbsent(threadIndex, k -> new ArrayList<>())
                        .add(new SummaryUpdate(item.path("text").asText(),
                                resolveSource(commentIndex, thread),
                                resolveTimestamp(commentIndex, thread),
                                item.path("topic").asText(null)));
            }
        }
        return updatesByThreadIndex;
    }

    private Optional<RedditComment> findComment(int commentIndex, RedditPost thread) {
        List<RedditComment> comments = thread.comments();
        if (commentIndex > 0 && commentIndex <= comments.size()) {
            return Optional.of(comments.get(commentIndex - 1));
        }
        return Optional.empty();
    }

    private String resolveSource(int commentIndex, RedditPost thread) {
        return findComment(commentIndex, thread)
                .map(RedditComment::permalink)
                .orElse(thread.permalink());
    }

    private long resolveTimestamp(int commentIndex, RedditPost thread) {
        return findComment(commentIndex, thread)
                .map(RedditComment::createdUtc)
                .orElse(thread.createdUtc());
    }
}
