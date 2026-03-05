package com.awardtravelupdates.model;

import com.awardtravelupdates.config.SummaryUpdateListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubredditSummary {

    @Id
    private String subreddit;

    @Convert(converter = SummaryUpdateListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<SummaryUpdate> updates;

    private Instant lastUpdated;

    private int postCount;
}
