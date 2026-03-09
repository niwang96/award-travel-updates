package com.awardtravelupdates.agent;

import com.awardtravelupdates.model.BlogPost;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.accessor.GroqAccessor;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractBlogSummaryAgent extends AbstractSummaryAgent {

    private static final String SYSTEM_PROMPT =
            REWARDS_ANALYST_CATEGORIES +
            "For category (8), treat any promotion that bundles loyalty points with a required cash payment as category (10) instead. " +
            "(9) Non-travel point/miles redemption opportunities — using credit card or loyalty points/miles for non-flight, non-hotel purposes such as event tickets, merchandise, or experiences at notable value. " +
            "(10) Promotional packages from travel brands where loyalty points are a significant component of the offer alongside a cash payment — include these even if a cash amount is required (e.g. 'Pay $199, get 3 nights + 100K points' from a hotel brand, timeshare presentations that award points). " +
            "Skip anything where airline miles or hotel loyalty points are NOT a meaningful part of the deal: " +
            "trip reports, opinion pieces, general news, debit cards, cashback-only products, " +
            "gift cards (buying, selling, or any promotions involving gift cards), shopping portal cashback offers, " +
            "bank account bonuses, fuel point promotions, " +
            "card-linked merchant discount programs that provide statement credits or percentage cashback at specific retailers (e.g. AmEx Offers at Lululemon, Chase Offers, Discover cashback promotions) — these have no meaningful airline or hotel points component, " +
            "general cash-savings deals (e.g. subscription discounts, shopping rewards sites, gift card arbitrage). " +
            "INCLUDE offers where airline miles or hotel loyalty points are a meaningful part of the value, even if cash is also involved (e.g. redeeming miles for event tickets, hotel packages that award loyalty points). " +
            "Note: shopping portal offers that earn loyalty miles or points (not cashback) qualify under category (8). " +
            REWARDS_ANALYST_BULLET_STYLE +
            "Return a JSON array of objects with \"text\" (the bullet), \"postIndex\" (1-based index of the post it came from), " +
            "and \"topic\" chosen from exactly these values: credit_cards, flights, hotels, lounges, status, deals. " +
            REWARDS_ANALYST_TOPIC_ROUTING +
            "categories 9-10 → deals. " +
            "Include at most two elements per post only if the post clearly covers two fully distinct qualifying topics; otherwise include at most one. If a post has nothing newsworthy, omit it. No markdown fences. " +
            "Example: [{\"text\": \"Chase added Wyndham as a new 1:1 transfer partner\", \"postIndex\": 2, \"topic\": \"credit_cards\"}, {\"text\": \"Amex offering 30% transfer bonus to Virgin Atlantic through Mar 31\", \"postIndex\": 5, \"topic\": \"credit_cards\"}, {\"text\": \"Delta offering 500 bonus miles for flights booked on routes to Europe through Apr 30\", \"postIndex\": 7, \"topic\": \"flights\"}, {\"text\": \"Hilton Honors launched status match offer for Marriott Gold members through Jun 30\", \"postIndex\": 9, \"topic\": \"status\"}, {\"text\": \"IHG One Rewards updated award chart with new pricing tiers across all brands\", \"postIndex\": 11, \"topic\": \"hotels\"}, {\"text\": \"American Express Centurion Lounge opening new location at LAX in Q3\", \"postIndex\": 13, \"topic\": \"lounges\"}]";

    public AbstractBlogSummaryAgent(GroqAccessor groqAccessor,
                                    PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqAccessor, postSummaryCacheRepository);
    }

    public abstract String getBlogId();

    public abstract String getDisplayName();

    public List<SummaryUpdate> summarize(List<BlogPost> posts) {
        if (posts.isEmpty()) {
            return fallbackOutput("No recent updates from " + getDisplayName() + " — check back soon.");
        }

        CachePartition<BlogPost> partition = partitionByCache(posts, BlogPost::url);
        List<SummaryUpdate> allUpdates = new ArrayList<>(partition.cachedUpdates());

        if (!partition.uncachedPosts().isEmpty()) {
            List<SummaryUpdate> newUpdates = callLlm(partition.uncachedPosts());
            saveUpdatesBySourceUrl(partition.uncachedPosts(), BlogPost::url, newUpdates);
            allUpdates.addAll(newUpdates);
        }

        return allUpdates.isEmpty()
                ? fallbackOutput("No recent updates from " + getDisplayName() + " — check back soon.")
                : allUpdates;
    }

    private List<SummaryUpdate> callLlm(List<BlogPost> posts) {
        String numberedPosts = IntStream.range(0, posts.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + posts.get(i).title() + "\n" + posts.get(i).content())
                .collect(Collectors.joining("\n\n"));
        JsonNode json = callApiJson(SYSTEM_PROMPT,
                "Summarize the key news and updates from these blog posts. Include an entry for every qualifying post — do not skip qualifying posts just because there are many:\n\n" + numberedPosts);
        return parseUpdates(json, posts,
                (item, post) -> new SummaryUpdate(item.path("text").asText(), post.url(), post.publishedUtc(), item.path("topic").asText(null)));
    }
}
