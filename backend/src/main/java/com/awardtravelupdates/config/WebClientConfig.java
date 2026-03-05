package com.awardtravelupdates.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10MB

    @Bean
    public WebClient redditClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://www.reddit.com")
                .defaultHeader("User-Agent", "AwardTravelUpdates/1.0")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
                        .build())
                .build();
    }

    @Bean
    public WebClient groqClient(WebClient.Builder builder, GroqProperties properties) {
        return builder
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }
}
