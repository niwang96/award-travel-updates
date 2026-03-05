package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.BlogPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractBlogSummaryAgent extends AbstractLlmAgent {

    private static final String SYSTEM_PROMPT =
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New transfer partners added by banks, " +
            "(2) Active transfer bonuses (include the bonus percentage, program names, and expiry if mentioned), " +
            "(3) New or limited-time card sign-up bonuses (include the full card name, points/miles amount, and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes), " +
            "(6) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(7) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(8) Limited-time loyalty program promotions — bonus miles/points for specific flights, hotel stays, or activities, and status match or challenge offers from airlines or hotels. " +
            "Skip trip reports, opinion pieces, general news, debit cards, cashback-only products, and anything that doesn't fit these categories. " +
            "Return a JSON array of objects with \"text\" (the bullet) and \"postIndex\" (1-based index of the post it came from). " +
            "At most one element per post. If a post has nothing newsworthy, omit it. No markdown fences. " +
            "Example: [{\"text\": \"Chase added Wyndham as 1:1 transfer partner\", \"postIndex\": 2}, {\"text\": \"Amex 30% transfer bonus to Virgin Atlantic through Mar 31\", \"postIndex\": 5}]";

    public AbstractBlogSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    public abstract String getBlogId();

    public abstract String getDisplayName();

    public Mono<AgentOutput> summarize(List<BlogPost> posts) {
        if (posts.isEmpty()) {
            return Mono.just(new AgentOutput(List.of(
                    new SummaryUpdate("No recent updates from " + getDisplayName() + " — check back soon.", null, null))));
        }

        String numberedPosts = IntStream.range(0, posts.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + posts.get(i).title() + "\n" + posts.get(i).content())
                .collect(Collectors.joining("\n\n"));

        return callApiJson(SYSTEM_PROMPT,
                "Summarize the key news and updates from these blog posts:\n\n" + numberedPosts)
                .map(json -> parseUpdates(json, posts))
                .map(updates -> {
                    if (updates.isEmpty()) {
                        return new AgentOutput(List.of(
                                new SummaryUpdate("No recent updates from " + getDisplayName() + " — check back soon.", null, null)));
                    }
                    return new AgentOutput(updates);
                });
    }

    private List<SummaryUpdate> parseUpdates(JsonNode json, List<BlogPost> posts) {
        List<SummaryUpdate> updates = new ArrayList<>();
        for (JsonNode item : json) {
            String text = item.path("text").asText();
            int postIndex = item.path("postIndex").asInt(0);
            if (postIndex >= 1 && postIndex <= posts.size()) {
                BlogPost post = posts.get(postIndex - 1);
                updates.add(new SummaryUpdate(text, post.url(), post.publishedUtc()));
            }
        }
        return updates;
    }
}
