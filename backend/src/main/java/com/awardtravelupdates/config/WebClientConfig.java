package com.awardtravelupdates.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient redditClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://www.reddit.com")
                .defaultHeader("User-Agent", "AwardTravelUpdates/1.0")
                .build();
    }
}
