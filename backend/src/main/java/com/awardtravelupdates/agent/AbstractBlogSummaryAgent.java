package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.AgentOutput;
import com.awardtravelupdates.model.BlogPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractBlogSummaryAgent extends AbstractLlmAgent {

    private static final String SYSTEM_PROMPT =
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New or limited-time card sign-up bonuses (include the full card name, points/miles amount, and spend requirement), " +
            "(2) Active transfer bonuses (include the bonus percentage, program names, and expiry if mentioned), " +
            "(3) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(4) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(5) Credit card benefit changes (lounge access, travel credits, annual fee changes). " +
            "Skip general news, opinion pieces, and anything that doesn't fit these categories. " +
            "Return a JSON array of concise bullet strings (no markdown fences). " +
            "Example: [\"Chase Sapphire Preferred: 100k UR points for $4k spend in 3 months\", \"Amex 30% transfer bonus to Virgin Atlantic through Mar 31\"]";

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

        return Flux.fromIterable(posts)
                .flatMap(post -> {
                    String postText = post.title() + "\n\n" + post.content();
                    return callApi(SYSTEM_PROMPT,
                            "Summarize the key news and updates from this blog post:\n\n" + postText)
                            .map(bullets -> bullets.stream()
                                    .map(text -> new SummaryUpdate(text, post.url(), post.publishedUtc()))
                                    .collect(Collectors.toList()));
                })
                .collectList()
                .map(lists -> {
                    List<SummaryUpdate> updates = lists.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    if (updates.isEmpty()) {
                        return new AgentOutput(List.of(
                                new SummaryUpdate("No recent updates from " + getDisplayName() + " — check back soon.", null, null)));
                    }
                    return new AgentOutput(updates);
                });
    }
}
