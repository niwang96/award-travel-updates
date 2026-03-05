package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.service.BlogSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogSummaryController {

    private final BlogSummaryService blogSummaryService;

    @GetMapping("/blog-summaries")
    public Map<String, SummaryResult> getBlogSummaries() {
        return blogSummaryService.getSummaries();
    }

    @GetMapping("/blog-summaries/{blogId}")
    public SummaryResult getBlogSummary(@PathVariable String blogId) {
        return blogSummaryService.getSummary(blogId);
    }
}
