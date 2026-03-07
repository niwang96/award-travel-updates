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
            "You are a credit card rewards analyst. Only report on these specific categories: " +
            "(1) New transfer partners added by banks, " +
            "(2) Active transfer bonuses (include the bonus percentage, program names, and expiry if mentioned), " +
            "(3) New, increased, or returning limited-time card sign-up bonuses (include the full card name, points/miles amount, and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes), " +
            "(6) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(7) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(8) Limited-time loyalty program promotions — bonus miles/points earned through normal loyalty activity such as specific flights, hotel stays, or shopping portal purchases; excludes cash-purchase promotional packages. " +
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
            "Always include the primary number (e.g. bonus points/miles amount or bonus percentage) — omit any item where this is not mentioned in the post. Spend requirements and expiry dates should be included when present but are not required to include the item. " +
            "Return a JSON array of objects with \"text\" (the bullet), \"postIndex\" (1-based index of the post it came from), " +
            "and \"topic\" chosen from exactly these values: credit_cards, flights, hotels, lounges, status, deals. " +
            "Topic assignment: categories 1-4 → credit_cards; category 5 → lounges; " +
            "categories 6-7 for airline/award programs → flights; categories 6-7 for hotel programs → hotels; " +
            "categories 6-7 for bank/credit card programs → credit_cards; " +
            "category 8 for status matches or challenges → status; category 8 for airline award sales or flight-specific mileage promotions → flights; all other category 8 items → deals; " +
            "categories 9-10 → deals. " +
            "At most one element per post. If a post has nothing newsworthy, omit it. No markdown fences. " +
            "Example: [{\"text\": \"Chase added Wyndham as 1:1 transfer partner\", \"postIndex\": 2, \"topic\": \"credit_cards\"}, {\"text\": \"Amex 30% transfer bonus to Virgin Atlantic through Mar 31\", \"postIndex\": 5, \"topic\": \"credit_cards\"}]";

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
