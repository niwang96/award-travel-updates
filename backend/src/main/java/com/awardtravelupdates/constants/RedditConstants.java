package com.awardtravelupdates.constants;

import java.util.List;
import java.util.Set;

public final class RedditConstants {

    private RedditConstants() {}

    public static final String SUBREDDIT_AWARD_TRAVEL = "awardtravel";
    public static final String SUBREDDIT_CHURNING = "churning";
    public static final List<String> SUBREDDITS = List.of(
            SUBREDDIT_AWARD_TRAVEL, SUBREDDIT_CHURNING);

    public static final Set<String> SUBREDDITS_WITH_COMMENTS = Set.of(SUBREDDIT_CHURNING);

    public static final String FIELD_DATA = "data";
    public static final String FIELD_CHILDREN = "children";
    public static final String FIELD_ID = "id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_SELFTEXT = "selftext";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_UPS = "ups";
    public static final String FIELD_CREATED_UTC = "created_utc";

    public static final int DEFAULT_LIMIT = 25;
    public static final int TOP_COMMENTS_LIMIT = 10;

    public static final String NEW_POSTS_URI = "/r/{subreddit}/new.json?limit={limit}";
    public static final String COMMENTS_URI = "/r/{subreddit}/comments/{id}.json?limit={limit}&depth=1";
}
