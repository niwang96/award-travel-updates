package com.awardtravelupdates.util;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;

import java.util.*;

public final class DeduplicationUtil {

    private DeduplicationUtil() {}

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "in", "on", "to", "and", "or", "of",
            "for", "with", "from", "by", "at", "it", "its", "that", "this",
            "as", "are", "was", "be", "has", "have", "had", "will", "which"
    );

    private static final double SIMILARITY_THRESHOLD = 0.25;

    private static final List<String> PRIORITY = List.of(
            "doctorofcredit", "frequentmiler", "awardtravel", "churning"
    );

    public static Map<String, SummaryResult> deduplicate(Map<String, SummaryResult> data) {
        List<Set<String>> seen = new ArrayList<>();
        Map<String, SummaryResult> result = new LinkedHashMap<>();

        for (String key : PRIORITY) {
            SummaryResult source = data.get(key);
            if (source == null) continue;

            List<SummaryUpdate> kept = new ArrayList<>();
            for (SummaryUpdate update : source.updates()) {
                Set<String> tokens = tokenize(update.text());
                boolean isDuplicate = seen.stream().anyMatch(s -> jaccard(tokens, s) >= SIMILARITY_THRESHOLD);
                if (!isDuplicate) {
                    seen.add(tokens);
                    kept.add(update);
                }
            }
            result.put(key, new SummaryResult(kept, source.stale()));
        }

        // Pass through any keys not in PRIORITY unchanged
        for (String key : data.keySet()) {
            if (!result.containsKey(key)) {
                result.put(key, data.get(key));
            }
        }

        return result;
    }

    private static Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("[^a-z]+")) {
            if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        long intersection = a.stream().filter(b::contains).count();
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection / union.size();
    }
}
