package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summaries")
    public Mono<Map<String, SummaryResult>> getSummaries() {
        return summaryService.getSummaries();
    }

    @GetMapping("/summaries/{subreddit}")
    public Mono<SummaryResult> getSummary(@PathVariable String subreddit) {
        return summaryService.getSummary(subreddit);
    }
}
