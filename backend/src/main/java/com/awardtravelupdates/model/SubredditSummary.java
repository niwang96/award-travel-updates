package com.awardtravelupdates.model;

import com.awardtravelupdates.config.StringListConverter;
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

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> bullets;

    private Instant lastUpdated;

    private int postCount;
}
