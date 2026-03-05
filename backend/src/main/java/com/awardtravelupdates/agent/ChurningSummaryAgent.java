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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

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
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"Chase added Wyndham as 1:1 transfer partner\", \"Amex 30% transfer bonus to Virgin Atlantic through Mar 31\"]";

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
                    new SummaryUpdate("No news and updates posts found.", null))));
        }

        return Flux.fromIterable(filtered)
                .flatMap(post -> {
                    String comments = post.comments().stream()
                            .map(c -> "  [" + c.upvotes() + " upvotes] " + c.body())
                            .collect(Collectors.joining("\n"));
                    String postText = "Post: " + post.title() + "\nComments:\n" + comments;
                    return callApi(SYSTEM_PROMPT,
                            "Summarize the key deals and updates from this churning post and its top comments:\n\n" + postText)
                            .map(bullets -> bullets.stream()
                                    .map(text -> new SummaryUpdate(text, post.permalink()))
                                    .collect(Collectors.toList()));
                })
                .collectList()
                .map(lists -> new AgentOutput(lists.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList())));
    }
}
