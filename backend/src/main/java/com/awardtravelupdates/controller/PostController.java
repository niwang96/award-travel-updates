package com.awardtravelupdates.controller;

import com.awardtravelupdates.model.RedditPost;
import com.awardtravelupdates.service.RedditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final RedditService redditService;

    @GetMapping("/posts")
    public Mono<List<RedditPost>> getPosts(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer hours) {
        return redditService.fetchAllPosts(limit, hours);
    }
}
