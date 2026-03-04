package com.awardtravelupdates.model;

import com.awardtravelupdates.config.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.List;

@Entity
public class SubredditSummary {

    @Id
    private String subreddit;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> bullets;

    private Instant lastUpdated;

    private int postCount;

    public SubredditSummary() {}

    public SubredditSummary(String subreddit, List<String> bullets, Instant lastUpdated, int postCount) {
        this.subreddit = subreddit;
        this.bullets = bullets;
        this.lastUpdated = lastUpdated;
        this.postCount = postCount;
    }

    public String getSubreddit() { return subreddit; }
    public List<String> getBullets() { return bullets; }
    public Instant getLastUpdated() { return lastUpdated; }
    public int getPostCount() { return postCount; }
}
