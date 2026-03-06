package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.service.CombinedSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CombinedSummaryController {

    private final CombinedSummaryService combinedSummaryService;

    @GetMapping("/combined-summaries")
    public Map<String, SummaryResult> getCombinedSummaries() {
        return combinedSummaryService.getSummaries();
    }
}
