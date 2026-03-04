package com.awardtravelupdates.service;

import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwardTravelSummaryAgent extends AbstractSummaryAgent {

    private static final String SYSTEM_PROMPT =
            "You are an award travel news analyst. Only include broadly applicable news and insights — NOT personal trip reports, " +
            "individual booking help requests, or one-off data points. " +
            "Focus on: program changes, award chart updates, airline/hotel partnership announcements, " +
            "devaluations or improvements to loyalty programs, and widely applicable redemption strategies. " +
            "A post qualifies only if it affects many travelers, not just one person's situation. " +
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"United raised Saver awards on transatlantic routes by 20%\", \"Air France-KLM Flying Blue adding dynamic pricing for all partners\"]";

    public AwardTravelSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public Mono<List<String>> summarize(List<RedditPost> posts) {
        List<RedditPost> filtered = posts.stream()
                .filter(p -> p.upvotes() > 0)
                .toList();

        if (filtered.isEmpty()) {
            return Mono.just(List.of("No upvoted awardtravel posts found."));
        }

        String postsText = filtered.stream()
                .map(post -> "[" + post.upvotes() + " upvotes] " + post.title() +
                        (post.selftext().isBlank() ? "" : "\n" + post.selftext()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return callApi(SYSTEM_PROMPT,
                "Summarize the key news and updates from these upvoted awardtravel posts:\n\n" + postsText);
    }
}
