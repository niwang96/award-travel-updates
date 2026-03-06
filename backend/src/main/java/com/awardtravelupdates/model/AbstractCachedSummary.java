package com.awardtravelupdates.model;

import com.awardtravelupdates.converter.SummaryUpdateListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@MappedSuperclass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractCachedSummary {

    @Convert(converter = SummaryUpdateListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<SummaryUpdate> updates;

    private Instant lastUpdated;
}
