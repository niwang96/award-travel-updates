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
public class SubredditSummary extends AbstractCachedSummary {

    @Id
    private String subreddit;

    public SubredditSummary(String subreddit, List<SummaryUpdate> updates, Instant lastUpdated) {
        super(updates, lastUpdated);
        this.subreddit = subreddit;
    }
}
