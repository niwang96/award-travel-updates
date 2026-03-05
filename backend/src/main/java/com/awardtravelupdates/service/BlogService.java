package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.model.BlogPost;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BlogService {

    private final RestClient rssClient;

    public BlogService(RestClient rssClient) {
        this.rssClient = rssClient;
    }

    public List<BlogPost> fetchPostsForBlog(String blogId) {
        String rssUrl = BlogConstants.BLOG_RSS_URLS.get(blogId);
        if (rssUrl == null) {
            return List.of();
        }
        try {
            String xml = rssClient.get()
                    .uri(rssUrl)
                    .retrieve()
                    .body(String.class);
            return parseFeed(xml, blogId);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<BlogPost> parseFeed(String xml, String blogId) {
        Instant cutoff = Instant.now().minus(BlogConstants.BLOG_POSTS_DAYS_LIMIT, ChronoUnit.DAYS);
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new StringReader(xml));
            return feed.getEntries().stream()
                    .filter(entry -> entry.getPublishedDate() != null &&
                            entry.getPublishedDate().toInstant().isAfter(cutoff))
                    .map(entry -> toBlogPost(entry, blogId))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private BlogPost toBlogPost(SyndEntry entry, String blogId) {
        String content = extractContent(entry);
        String stripped = stripHtml(content);
        long publishedUtc = entry.getPublishedDate().toInstant().getEpochSecond();
        return new BlogPost(blogId, entry.getTitle(), stripped, entry.getLink(), publishedUtc);
    }

    private String extractContent(SyndEntry entry) {
        if (entry.getDescription() != null) {
            return entry.getDescription().getValue();
        }
        if (!entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }
        return "";
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        String stripped = html.replaceAll("<[^>]+>", " ").replaceAll("\\s{2,}", " ").trim();
        return stripped.length() > BlogConstants.BLOG_MAX_CONTENT_CHARS
                ? stripped.substring(0, BlogConstants.BLOG_MAX_CONTENT_CHARS)
                : stripped;
    }
}
