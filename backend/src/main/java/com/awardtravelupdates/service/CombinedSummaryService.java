package com.awardtravelupdates.service;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.util.DeduplicationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class CombinedSummaryService {

    private final BlogSummaryService blogSummaryService;
    private final SubredditSummaryService subSummaryService;

    public Map<String, SummaryResult> getSummaries() {
        Map<String, SummaryResult> blog;
        Map<String, SummaryResult> sub;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var blogFuture = CompletableFuture.supplyAsync(blogSummaryService::getSummaries, executor);
            var subFuture = CompletableFuture.supplyAsync(subSummaryService::getSummaries, executor);
            blog = blogFuture.join();
            sub = subFuture.join();
        }

        Map<String, SummaryResult> merged = new LinkedHashMap<>();
        merged.putAll(blog);
        merged.putAll(sub);

        return DeduplicationUtil.deduplicate(merged);
    }
}
