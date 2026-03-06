package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.service.GmailAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmailDealsController {

    private final GmailAccessor gmailAccessor;

    @GetMapping("/email-deals")
    public List<SummaryUpdate> getEmailDeals() {
        return gmailAccessor.fetchRecentDeals();
    }
}
