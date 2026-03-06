package com.awardtravelupdates.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostSummaryCache {

    @Id
    @Column(length = 512)
    private String url;

    // null = processed but not relevant; JSON array of SummaryUpdate objects if relevant
    @Column(columnDefinition = "TEXT")
    private String summaryText;

    private Instant processedAt;
}
