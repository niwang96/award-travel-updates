package com.awardtravelupdates.model;

import java.util.List;

public record SummaryResult(List<SummaryUpdate> updates, boolean stale) {}
