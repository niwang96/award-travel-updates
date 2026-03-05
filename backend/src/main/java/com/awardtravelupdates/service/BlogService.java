package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.model.BlogPost;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BlogService {

    private final WebClient rssClient;

    public BlogService(WebClient rssClient) {
        this.rssClient = rssClient;
    }

    public Mono<List<BlogPost>> fetchPostsForBlog(String blogId) {
        String rssUrl = BlogConstants.BLOG_RSS_URLS.get(blogId);
        if (rssUrl == null) {
            return Mono.just(List.of());
        }

        return rssClient.get()
                .uri(rssUrl)
                .retrieve()
                .bodyToMono(String.class)
                .publishOn(Schedulers.boundedElastic())
                .map(xml -> parseFeed(xml, blogId))
                .onErrorReturn(List.of());
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
        if (!entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }
        if (entry.getDescription() != null) {
            return entry.getDescription().getValue();
        }
        return "";
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
