package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.service.RedditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {

    private final RedditService redditService;

    public PostController(RedditService redditService) {
        this.redditService = redditService;
    }

    @GetMapping("/posts")
    public Mono<List<RedditPost>> getPosts() {
        return redditService.fetchAllPosts();
    }
}
