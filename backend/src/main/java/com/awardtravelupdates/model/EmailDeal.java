package com.awardtravelupdates.model;

public record EmailDeal(
        int points,
        String airline,
        String cabin,
        String origin,
        String destination,
        String via,
        String flightDate,
        String redemptionProgram,
        String url,
        String emailSubject,
        long receivedAt
) {}
