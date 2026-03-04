package com.awardtravelupdates.service;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class RedditService {

    private final WebClient redditClient;

    public RedditService(WebClient redditClient) {
        this.redditClient = redditClient;
    }

    public Mono<List<RedditPost>> fetchAllPosts() {
        return Flux.fromIterable(RedditConstants.SUBREDDITS)
                .flatMap(subreddit -> fetchPosts(subreddit, RedditConstants.SUBREDDITS_WITH_COMMENTS.contains(subreddit)))
                .collectList();
    }

    private Flux<RedditPost> fetchPosts(String subreddit, boolean withComments) {
        return redditClient.get()
                .uri(RedditConstants.NEW_POSTS_URI, subreddit)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(body -> {
                    JsonNode children = body.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_CHILDREN);
                    return Flux.fromIterable(toList(children))
                            .flatMap(child -> {
                                JsonNode postData = child.path(RedditConstants.FIELD_DATA);
                                String id = postData.path(RedditConstants.FIELD_ID).asText();
                                String title = postData.path(RedditConstants.FIELD_TITLE).asText();
                                String selftext = postData.path(RedditConstants.FIELD_SELFTEXT).asText();

                                if (withComments) {
                                    return fetchComments(subreddit, id)
                                            .map(comments -> new RedditPost(subreddit, title, selftext, comments));
                                }
                                return Mono.just(new RedditPost(subreddit, title, selftext, List.of()));
                            });
                });
    }

    private Mono<List<String>> fetchComments(String subreddit, String postId) {
        return redditClient.get()
                .uri(RedditConstants.COMMENTS_URI, subreddit, postId)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .skip(1) // first element is the post, second is comments
                .next()
                .map(commentsListing -> {
                    JsonNode children = commentsListing.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_CHILDREN);
                    return toList(children).stream()
                            .map(child -> child.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_BODY))
                            .filter(body -> !body.isMissingNode())
                            .map(JsonNode::asText)
                            .toList();
                })
                .onErrorReturn(List.of());
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).toList();
    }
}
