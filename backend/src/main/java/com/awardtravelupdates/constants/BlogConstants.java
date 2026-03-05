package com.awardtravelupdates.constants;

import java.util.Map;

public final class BlogConstants {

    private BlogConstants() {}

    public static final String BLOG_DOCTOR_OF_CREDIT = "doctorofcredit";
    public static final String BLOG_FREQUENT_MILER = "frequentmiler";

    public static final Map<String, String> BLOG_RSS_URLS = Map.of(
            BLOG_DOCTOR_OF_CREDIT, "https://www.doctorofcredit.com/feed/",
            BLOG_FREQUENT_MILER, "https://frequentmiler.com/feed/"
    );

    public static final int BLOG_POSTS_DAYS_LIMIT = 3;
    public static final int BLOG_STALE_HOURS = 3;
    public static final int BLOG_NEW_POSTS_THRESHOLD = 3;
}
