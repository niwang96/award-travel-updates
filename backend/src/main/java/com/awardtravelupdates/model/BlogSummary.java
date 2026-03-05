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
public class BlogSummary extends AbstractCachedSummary {

    @Id
    private String blogId;

    public BlogSummary(String blogId, List<SummaryUpdate> updates, Instant lastUpdated, int postCount) {
        super(updates, lastUpdated, postCount);
        this.blogId = blogId;
    }
}
