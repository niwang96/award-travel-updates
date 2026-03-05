package com.awardtravelupdates.service;

import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChurningSummaryAgent extends AbstractSummaryAgent {

    private static final String NEWS_FILTER_KEYWORD = "news and updates";

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
    public Mono<List<String>> summarize(List<RedditPost> posts) {
        List<RedditPost> filtered = posts.stream()
                .filter(p -> p.title().toLowerCase().contains(NEWS_FILTER_KEYWORD))
                .toList();

        if (filtered.isEmpty()) {
            return Mono.just(List.of("No news and updates posts found."));
        }

        String postsText = filtered.stream()
                .map(post -> {
                    String comments = post.comments().stream()
                            .map(c -> "  [" + c.upvotes() + " upvotes] " + c.body())
                            .collect(Collectors.joining("\n"));
                    return "Post: " + post.title() + "\nComments:\n" + comments;
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        return callApi(SYSTEM_PROMPT,
                "Summarize the key deals and updates from these churning posts and their top comments:\n\n" + postsText);
    }
}
