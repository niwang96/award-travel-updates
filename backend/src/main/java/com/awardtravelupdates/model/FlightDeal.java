package com.awardtravelupdates.model;

public record FlightDeal(
        int points,
        String airline,
        String cabin,
        String origin,
        String destination,
        String flightDate,
        String bookingProgram,
        String source,
        long dateFound
) {}
