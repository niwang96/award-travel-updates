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
            "(3) New or limited-time card sign-up bonuses (include the full card name, points/miles amount, and spend requirement), " +
            "(4) Changes to existing transfer partner ratios or program terms, " +
            "(5) Lounge news (new openings, closures, or access policy changes), " +
            "(6) Award chart updates — a program publishing new mileage rates or pricing tiers, " +
            "(7) Program changes — a loyalty program changing its rules, policies, partnerships, or earning/redemption structure, " +
            "(8) Limited-time loyalty program promotions — bonus miles/points for specific flights, hotel stays, or activities, and status match or challenge offers from airlines or hotels. " +
            "Skip trip reports, opinion pieces, general news, debit cards, cashback-only products, " +
            "gift cards (buying, selling, or any promotions involving gift cards), shopping portal cashback offers, " +
            "bank account bonuses, fuel point promotions, credit card statement credits or merchant offers, " +
            "and anything that doesn't fit these categories. " +
            "Note: shopping portal offers that earn loyalty miles or points (not cashback) qualify under category (8). " +
            "Always include specific numbers (point amounts, bonus percentages, spend requirements) — omit any item where the key number is not mentioned in the post. " +
            "Return a JSON array of objects with \"text\" (the bullet), \"postIndex\" (1-based index of the post it came from), " +
            "and \"topic\" chosen from exactly these values: credit_cards, flights, hotels, lounges, status, deals. " +
            "Topic assignment: categories 1-4 → credit_cards; category 5 → lounges; " +
            "categories 6-7 for airline/award programs → flights; categories 6-7 for hotel programs → hotels; " +
            "categories 6-7 for bank/credit card programs → credit_cards; " +
            "category 8 for status matches or challenges → status; all other category 8 items → deals. " +
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
                "Summarize the key news and updates from these blog posts:\n\n" + numberedPosts);
        return parseUpdates(json, posts,
                (item, post) -> new SummaryUpdate(item.path("text").asText(), post.url(), post.publishedUtc(), item.path("topic").asText(null)));
    }
}
