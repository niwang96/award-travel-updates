package com.awardtravelupdates.service;

import com.awardtravelupdates.accessor.GmailAccessor;
import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.model.EmailDealsSummary;
import com.awardtravelupdates.model.SummaryUpdate;
import com.awardtravelupdates.repository.EmailDealsSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDealsSummaryService {

    private static final String EMAIL_DEALS_ID = "email-deals";

    private final GmailAccessor gmailAccessor;
    private final EmailDealsSummaryRepository emailDealsSummaryRepository;

    public List<SummaryUpdate> getDeals() {
        Optional<EmailDealsSummary> cached = emailDealsSummaryRepository.findById(EMAIL_DEALS_ID);

        if (cached.isPresent() && !isStale(cached.get())) {
            log.info("Returning cached email deals");
            return cached.get().getUpdates();
        }

        try {
            log.info("Fetching fresh email deals from Gmail");
            List<SummaryUpdate> updates = gmailAccessor.fetchRecentDeals();
            emailDealsSummaryRepository.save(new EmailDealsSummary(EMAIL_DEALS_ID, updates, Instant.now()));
            return updates;
        } catch (Exception e) {
            log.error("Failed to fetch email deals from Gmail", e);
            return cached.map(EmailDealsSummary::getUpdates).orElse(List.of());
        }
    }

    private boolean isStale(EmailDealsSummary summary) {
        return summary.getLastUpdated().isBefore(Instant.now().minus(BlogConstants.BLOG_STALE_HOURS, ChronoUnit.HOURS));
    }
}
