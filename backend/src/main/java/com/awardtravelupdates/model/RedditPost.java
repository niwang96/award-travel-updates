package com.awardtravelupdates.model;

import java.util.List;

public record RedditPost(String subreddit, String title, String selftext, int upvotes, long createdUtc, List<RedditComment> comments) {}
