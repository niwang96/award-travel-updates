package com.awardtravelupdates.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient redditClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://www.reddit.com")
                .defaultHeader("User-Agent", "AwardTravelUpdates/1.0")
                .build();
    }

    @Bean
    public RestClient groqClient(RestClient.Builder builder, GroqProperties properties) {
        return builder
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Bean
    public RestClient rssClient(RestClient.Builder builder) {
        return builder
                .defaultHeader("User-Agent", "AwardTravelUpdates/1.0")
                .build();
    }

    @Bean
    public RestClient googleAuthClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://oauth2.googleapis.com")
                .build();
    }

    @Bean
    public RestClient gmailApiClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://gmail.googleapis.com")
                .build();
    }
}
