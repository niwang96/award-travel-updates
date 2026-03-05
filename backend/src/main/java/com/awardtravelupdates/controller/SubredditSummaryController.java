package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.service.SubredditSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubredditSummaryController {

    private final SubredditSummaryService subredditSummaryService;

    @GetMapping("/summaries")
    public Map<String, SummaryResult> getSummaries() {
        return subredditSummaryService.getSummaries();
    }

    @GetMapping("/summaries/{subreddit}")
    public SummaryResult getSummary(@PathVariable String subreddit) {
        return subredditSummaryService.getSummary(subreddit);
    }
}
