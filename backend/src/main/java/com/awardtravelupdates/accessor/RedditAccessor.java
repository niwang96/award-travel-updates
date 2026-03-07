package com.awardtravelupdates.accessor;

import com.awardtravelupdates.constants.RedditConstants;
import com.awardtravelupdates.model.RedditComment;
import com.awardtravelupdates.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedditAccessor {

    private final RestClient redditClient;

    public List<RedditPost> fetchPostsForSubreddit(String subreddit) {
        boolean withComments = RedditConstants.SUBREDDITS_WITH_COMMENTS.contains(subreddit);
        boolean useTopPosts = RedditConstants.SUBREDDITS_USING_TOP_POSTS.contains(subreddit);
        int minUpvotes = RedditConstants.MIN_UPVOTES.getOrDefault(subreddit, 0);
        String uri = useTopPosts ? RedditConstants.TOP_POSTS_URI : RedditConstants.NEW_POSTS_URI;

        try {
            int limit = useTopPosts ? RedditConstants.TOP_POSTS_LIMIT : RedditConstants.DEFAULT_LIMIT;
            JsonNode body = redditClient.get()
                    .uri(uri, subreddit, limit)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode children = body.path(RedditConstants.FIELD_DATA).path(RedditConstants.FIELD_CHILDREN);
            return toList(children).stream()
                    .map(child -> toPost(child, subreddit, withComments))
                    .filter(Objects::nonNull)
                    .filter(p -> p.upvotes() >= minUpvotes)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch posts for r/{}: {}", subreddit, e.getMessage());
            return List.of();
        }
    }

    private RedditPost toPost(JsonNode child, String subreddit, boolean withComments) {
        JsonNode postData = child.path(RedditConstants.FIELD_DATA);
        String id = postData.path(RedditConstants.FIELD_ID).asText();
        String title = postData.path(RedditConstants.FIELD_TITLE).asText();
        String selftext = postData.path(RedditConstants.FIELD_SELFTEXT).asText();
        int upvotes = postData.path(RedditConstants.FIELD_UPS).asInt();
        long createdUtc = postData.path(RedditConstants.FIELD_CREATED_UTC).asLong();
        String permalink = RedditConstants.REDDIT_BASE_URL + postData.path(RedditConstants.FIELD_PERMALINK).asText();

        String titleFilter = RedditConstants.COMMENT_TITLE_FILTERS.get(subreddit);
        boolean shouldFetchComments = withComments &&
                (titleFilter == null || title.toLowerCase().contains(titleFilter));

        List<RedditComment> comments = shouldFetchComments
                ? fetchComments(subreddit, id)
                : List.of();

        return new RedditPost(subreddit, title, selftext, upvotes, createdUtc, comments, permalink);
    }

    private List<RedditComment> fetchComments(String subreddit, String postId) {
        try {
            // Response is a JSON array: [post listing, comments listing]
            JsonNode response = redditClient.get()
                    .uri(RedditConstants.COMMENTS_URI, subreddit, postId, RedditConstants.TOP_COMMENTS_LIMIT)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode children = response.get(1)
                    .path(RedditConstants.FIELD_DATA)
                    .path(RedditConstants.FIELD_CHILDREN);

            return toList(children).stream()
                    .map(child -> child.path(RedditConstants.FIELD_DATA))
                    .filter(data -> !data.path(RedditConstants.FIELD_BODY).isMissingNode())
                    .filter(data -> !RedditConstants.BOT_AUTHORS.contains(data.path(RedditConstants.FIELD_AUTHOR).asText()))
                    .map(data -> new RedditComment(
                            data.path(RedditConstants.FIELD_AUTHOR).asText(),
                            data.path(RedditConstants.FIELD_BODY).asText(),
                            data.path(RedditConstants.FIELD_UPS).asInt(),
                            data.path(RedditConstants.FIELD_CREATED_UTC).asLong(),
                            RedditConstants.REDDIT_BASE_URL + data.path(RedditConstants.FIELD_PERMALINK).asText()))
                    .sorted(Comparator.comparingInt(RedditComment::upvotes).reversed())
                    .limit(RedditConstants.TOP_COMMENTS_LIMIT)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch comments for r/{} post {}: {}", subreddit, postId, e.getMessage());
            return List.of();
        }
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).toList();
    }
}
