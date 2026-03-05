package com.awardtravelupdates.model;

public record RedditComment(String body, int upvotes, long createdUtc, String permalink) {}
