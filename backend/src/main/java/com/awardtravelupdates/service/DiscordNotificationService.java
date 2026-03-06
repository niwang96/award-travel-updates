package com.awardtravelupdates.service;

import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordNotificationService {

    private static final Map<String, String> SECTION_LABELS = Map.of(
            "doctorofcredit", "Doctor of Credit",
            "frequentmiler", "Frequent Miler",
            "awardtravel", "r/awardtravel",
            "churning", "r/churning"
    );

    private static final List<String> PRIORITY = List.of(
            "doctorofcredit", "frequentmiler", "awardtravel", "churning"
    );

    private static final int DISCORD_MAX_CHARS = 2000;

    private final CombinedSummaryService combinedSummaryService;
    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordNotificationService(CombinedSummaryService combinedSummaryService,
                                      @Value("${discord.webhook.url:}") String webhookUrl,
                                      RestClient.Builder builder) {
        this.combinedSummaryService = combinedSummaryService;
        this.webhookUrl = webhookUrl;
        this.restClient = builder.build();
    }

    public void send() {
        if (webhookUrl.isBlank()) {
            throw new IllegalStateException("discord.webhook.url is not configured in application.properties");
        }

        Map<String, SummaryResult> data = combinedSummaryService.getSummaries();
        List<String> messages = formatMessages(data);

        log.info("Sending {} Discord message(s)", messages.size());
        for (String message : messages) {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
        }
        log.info("Discord notification sent successfully");
    }

    private List<String> formatMessages(Map<String, SummaryResult> data) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String key : PRIORITY) {
            SummaryResult result = data.get(key);
            if (result == null || result.updates().isEmpty()) continue;

            String label = SECTION_LABELS.getOrDefault(key, key);
            StringBuilder section = new StringBuilder();
            section.append("**").append(label).append("**\n");
            for (SummaryUpdate update : result.updates()) {
                section.append("• ").append(update.text()).append("\n");
            }
            section.append("\n");

            String chunk = section.toString();
            if (current.length() + chunk.length() > DISCORD_MAX_CHARS) {
                if (!current.isEmpty()) {
                    messages.add(current.toString().trim());
                    current = new StringBuilder();
                }
                if (chunk.length() > DISCORD_MAX_CHARS) {
                    messages.add(chunk.substring(0, DISCORD_MAX_CHARS - 3) + "...");
                    continue;
                }
            }
            current.append(chunk);
        }

        if (!current.isEmpty()) {
            messages.add(current.toString().trim());
        }

        return messages;
    }
}
