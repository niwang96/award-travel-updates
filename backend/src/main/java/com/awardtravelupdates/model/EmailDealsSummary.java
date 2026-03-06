package com.awardtravelupdates.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class EmailDealsSummary extends AbstractCachedSummary {

    @Id
    private String id;

    public EmailDealsSummary(String id, List<SummaryUpdate> updates, Instant lastUpdated) {
        super(updates, lastUpdated);
        this.id = id;
    }
}
