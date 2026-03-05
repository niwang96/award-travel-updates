package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditComment;
import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class RedditService {

    private final WebClient redditClient;

    public Mono<List<RedditPost>> fetchPostsForSubreddit(String subreddit) {
        boolean withComments = RedditConstants.SUBREDDITS_WITH_COMMENTS.contains(subreddit);
        return fetchPosts(subreddit, withComments, RedditConstants.DEFAULT_LIMIT, null)
                .collectList();
    }

    private Flux<RedditPost> fetchPosts(String subreddit, boolean withComments, int postLimit, Instant createdAfter) {
        boolean useTopPosts = RedditConstants.SUBREDDITS_USING_TOP_POSTS.contains(subreddit);
        Instant effectiveCreatedAfter = useTopPosts
                ? Instant.now().minus(RedditConstants.TOP_POSTS_DAYS, ChronoUnit.DAYS)
                : createdAfter;
        String uri = useTopPosts ? RedditConstants.TOP_POSTS_URI : RedditConstants.NEW_POSTS_URI;

        return redditClient.get()
                .uri(uri, subreddit, postLimit)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(body -> {
                    JsonNode children = body.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_CHILDREN);
                    return Flux.fromIterable(toList(children))
                            .flatMap(child -> toPost(child, subreddit, withComments, effectiveCreatedAfter));
                });
    }

    private Mono<RedditPost> toPost(JsonNode child, String subreddit, boolean withComments, Instant createdAfter) {
        JsonNode postData = child.path(RedditConstants.FIELD_DATA);
        String id = postData.path(RedditConstants.FIELD_ID).asText();
        String title = postData.path(RedditConstants.FIELD_TITLE).asText();
        String selftext = postData.path(RedditConstants.FIELD_SELFTEXT).asText();
        int upvotes = postData.path(RedditConstants.FIELD_UPS).asInt();
        long createdUtc = postData.path(RedditConstants.FIELD_CREATED_UTC).asLong();
        String permalink = RedditConstants.REDDIT_BASE_URL + postData.path(RedditConstants.FIELD_PERMALINK).asText();

        if (isBefore(createdUtc, createdAfter)) {
            return Mono.empty();
        }
        String titleFilter = RedditConstants.COMMENT_TITLE_FILTERS.get(subreddit);
        boolean shouldFetchComments = withComments &&
                (titleFilter == null || title.toLowerCase().contains(titleFilter));
        if (shouldFetchComments) {
            return fetchComments(subreddit, id, createdAfter)
                    .map(comments -> new RedditPost(subreddit, title, selftext, upvotes, createdUtc, comments, permalink));
        }
        return Mono.just(new RedditPost(subreddit, title, selftext, upvotes, createdUtc, List.of(), permalink));
    }

    private boolean isBefore(long createdUtc, Instant cutoff) {
        return cutoff != null && Instant.ofEpochSecond(createdUtc).isBefore(cutoff);
    }

    private Mono<List<RedditComment>> fetchComments(String subreddit, String postId, Instant createdAfter) {
        return redditClient.get()
                .uri(RedditConstants.COMMENTS_URI, subreddit, postId, RedditConstants.TOP_COMMENTS_LIMIT)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .skip(1) // first element is the post, second is comments
                .next()
                .map(commentsListing -> {
                    JsonNode children = commentsListing.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_CHILDREN);
                    return toList(children).stream()
                            .map(child -> child.path(RedditConstants.FIELD_DATA))
                            .filter(data -> !data.path(RedditConstants.FIELD_BODY).isMissingNode())
                            .filter(data -> !isBefore(data.path(RedditConstants.FIELD_CREATED_UTC).asLong(), createdAfter))
                            .map(data -> new RedditComment(
                                    data.path(RedditConstants.FIELD_BODY).asText(),
                                    data.path(RedditConstants.FIELD_UPS).asInt(),
                                    data.path(RedditConstants.FIELD_CREATED_UTC).asLong(),
                                    RedditConstants.REDDIT_BASE_URL + data.path(RedditConstants.FIELD_PERMALINK).asText()))
                            .sorted(Comparator.comparingInt(RedditComment::upvotes).reversed())
                            .limit(RedditConstants.TOP_COMMENTS_LIMIT)
                            .toList();
                })
                .onErrorReturn(List.of());
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).toList();
    }
}
