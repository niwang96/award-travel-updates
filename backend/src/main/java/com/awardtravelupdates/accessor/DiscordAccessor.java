package com.awardtravelupdates.accessor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class DiscordAccessor {

    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordAccessor(@Value("${discord.webhook.url:}") String webhookUrl,
                           RestClient.Builder builder) {
        this.webhookUrl = webhookUrl;
        this.restClient = builder.build();
    }

    public void sendMessage(String content) {
        if (webhookUrl.isBlank()) {
            throw new IllegalStateException("discord.webhook.url is not configured in application.properties");
        }

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content))
                .retrieve()
                .toBodilessEntity();
    }
}
