package com.awardtravelupdates.model;

import com.awardtravelupdates.converter.FlightDealListConverter;
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
public class EmailDealsSummary {

    @Id
    private String id;

    @Convert(converter = FlightDealListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<FlightDeal> deals;

    private Instant lastUpdated;

}
