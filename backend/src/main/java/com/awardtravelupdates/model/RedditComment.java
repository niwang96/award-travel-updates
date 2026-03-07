package com.awardtravelupdates.model;

public record RedditComment(String author, String body, int upvotes, long createdUtc, String permalink) {}
