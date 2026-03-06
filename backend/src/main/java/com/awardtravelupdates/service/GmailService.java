package com.awardtravelupdates.service;

import com.awardtravelupdates.config.GoogleProperties;
import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private static final String GMAIL_QUERY = "from:hello@roame.travel label:Flight Deals";
    private static final int MAX_EMAILS = 10;

    // Case-sensitive PTS match with negative lookbehind to avoid matching mid-number (e.g. "57,5000 PTS" → "000 PTS")
    // Also avoids matching prose "57,500 points" since we require uppercase PTS
    // Captures the [N] link number immediately after PTS so we can look up the deal URL
    private static final Pattern DEAL_BLOCK_PATTERN = Pattern.compile(
            "(?<![,\\d])(\\d{1,3}(?:,\\d{3})*)\\s+PTS\\s+\\[(\\d+)\\]");

    // Parses the "Links:" footer at the bottom of the email body
    private static final Pattern LINK_MAP_PATTERN = Pattern.compile(
            "\\[(\\d+)\\]\\s+(https?://\\S+)");

    private static final Pattern REDEEM_PATTERN = Pattern.compile(
            "Redeem via\\s+([^\r\n]+)",
            Pattern.CASE_INSENSITIVE);

    // Matches the structured fields within a single normalized deal line
    private static final Pattern DEAL_FIELDS_PATTERN = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})*)\\s+PTS\\s+" +
            "(.+?)\\s+FROM\\s+" +
            "(.+?)\\s+TO\\s+" +
            "(.+?)(?:\\s+\\(VIA\\s+([A-Z0-9]+)\\))?\\s+ON\\s+" +
            "(\\d+/\\d+/\\d+)" +
            ".+?Redeem via\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    private final RestClient googleAuthClient;
    private final RestClient gmailApiClient;
    private final GoogleProperties googleProperties;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public List<SummaryUpdate> fetchRecentDeals() {
        try {
            String accessToken = getAccessToken();
            List<String> messageIds = searchMessages(accessToken);
            List<SummaryUpdate> results = new ArrayList<>();
            for (String id : messageIds) {
                results.addAll(fetchDealsFromMessage(accessToken, id));
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to fetch email deals: {}", e.getMessage());
            return List.of();
        }
    }

    private String getAccessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry.minus(60, ChronoUnit.SECONDS))) {
            return cachedAccessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleProperties.clientId());
        form.add("client_secret", googleProperties.clientSecret());
        form.add("refresh_token", googleProperties.gmailRefreshToken());
        form.add("grant_type", "refresh_token");

        JsonNode response = googleAuthClient.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        cachedAccessToken = response.path("access_token").asText();
        int expiresIn = response.path("expires_in").asInt(3600);
        tokenExpiry = Instant.now().plus(expiresIn, ChronoUnit.SECONDS);
        return cachedAccessToken;
    }

    private List<String> searchMessages(String accessToken) {
        JsonNode response = gmailApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/gmail/v1/users/me/messages")
                        .queryParam("q", GMAIL_QUERY)
                        .queryParam("maxResults", MAX_EMAILS)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        List<String> ids = new ArrayList<>();
        for (JsonNode msg : response.path("messages")) {
            ids.add(msg.path("id").asText());
        }
        return ids;
    }

    private List<SummaryUpdate> fetchDealsFromMessage(String accessToken, String messageId) {
        try {
            JsonNode message = gmailApiClient.get()
                    .uri("/gmail/v1/users/me/messages/{id}?format=full", messageId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            long receivedAt = message.path("internalDate").asLong() / 1000;
            JsonNode payload = message.path("payload");
            String subject = extractHeader(payload.path("headers"), "Subject");
            String body = extractTextBody(payload);

            if (body.isBlank()) {
                log.warn("Empty body for message {}", messageId);
                return List.of();
            }

            return parseDeals(body, subject, receivedAt);
        } catch (Exception e) {
            log.error("Failed to fetch message {}: {}", messageId, e.getMessage());
            return List.of();
        }
    }

    private String extractHeader(JsonNode headers, String name) {
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(header.path("name").asText())) {
                return header.path("value").asText();
            }
        }
        return "";
    }

    private String extractTextBody(JsonNode payload) {
        String mimeType = payload.path("mimeType").asText();

        if (mimeType.equals("text/plain")) {
            String data = payload.path("body").path("data").asText();
            if (!data.isBlank()) {
                return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
            }
        }

        // Recurse into parts
        for (JsonNode part : payload.path("parts")) {
            String result = extractTextBody(part);
            if (!result.isBlank()) {
                return result;
            }
        }

        return "";
    }

    private List<SummaryUpdate> parseDeals(String body, String subject, long receivedAt) {
        // Extract link map from the "Links:" footer before removing [N] markers
        Map<String, String> linkMap = new HashMap<>();
        Matcher linkMatcher = LINK_MAP_PATTERN.matcher(body);
        while (linkMatcher.find()) {
            linkMap.put(linkMatcher.group(1), linkMatcher.group(2));
        }

        // Normalize: preserve [N] markers for link number capture, clean whitespace
        // Also fix typos like "57,5000 PTS" → "57,500 PTS" (extra digit after thousands separator)
        String originalNormalized = body
                .replace("\u00a0", " ")
                .replaceAll("\r\n", "\n")
                .replaceAll("(\\d{1,3}),(\\d{3})\\d+\\s+PTS", "$1,$2 PTS");

        List<SummaryUpdate> deals = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher startMatcher = DEAL_BLOCK_PATTERN.matcher(originalNormalized);

        while (startMatcher.find()) {
            int blockStart = startMatcher.start();
            String linkNumber = startMatcher.group(2);
            String dealUrl = linkMap.get(linkNumber);

            // Find the "Redeem via" line after this deal header (in original to keep position alignment)
            Matcher redeemMatcher = REDEEM_PATTERN.matcher(originalNormalized);
            if (!redeemMatcher.find(blockStart)) {
                continue;
            }

            // Extract raw block, strip [N] markers, then flatten into a single line for field parsing
            String block = originalNormalized.substring(blockStart, redeemMatcher.end())
                    .replaceAll("\\[\\d+\\]", "")
                    .replaceAll("\n+", " ")
                    .replaceAll(" {2,}", " ")
                    .trim();

            // Skip pro-only deals that don't have full route info
            String blockUpper = block.toUpperCase();
            if (blockUpper.contains("ROAME PRO") || blockUpper.contains("FULL DATES FOR")) {
                continue;
            }

            Matcher fieldsMatcher = DEAL_FIELDS_PATTERN.matcher(block);
            if (!fieldsMatcher.find()) {
                log.warn("Could not parse deal block: {}", block);
                continue;
            }

            try {
                int points = Integer.parseInt(fieldsMatcher.group(1).replace(",", ""));
                String airlineAndCabin = fieldsMatcher.group(2).trim();
                String via = fieldsMatcher.group(5) != null ? fieldsMatcher.group(5).trim() : null;
                String flightDate = fieldsMatcher.group(6).trim();
                String redemptionProgram = fieldsMatcher.group(7).trim();

                String cabin = extractCabin(airlineAndCabin);
                String airline = extractAirline(airlineAndCabin, cabin);
                String origin = toTitleCase(fieldsMatcher.group(3).trim());
                String destination = toTitleCase(fieldsMatcher.group(4).trim());

                // Deduplicate by key fields
                String dedupeKey = points + "|" + airline + "|" + origin + "|" + destination + "|" + flightDate;
                if (!seen.add(dedupeKey)) {
                    continue;
                }

                String viaClause = (via != null && !via.isBlank()) ? " (via " + via + ")" : "";
                String text = String.format("%,d pts %s %s from %s to %s%s on %s booked through %s",
                        points, airline, cabin, origin, destination, viaClause, flightDate, redemptionProgram);
                deals.add(new SummaryUpdate(text, dealUrl, receivedAt));
            } catch (Exception e) {
                log.warn("Failed to parse deal fields from block: {}", block);
            }
        }

        return deals;
    }

    private String extractCabin(String airlineAndCabin) {
        String upper = airlineAndCabin.toUpperCase();
        if (upper.contains("FIRST CLASS")) return "First Class";
        if (upper.contains("BUSINESS CLASS")) return "Business Class";
        if (upper.contains("ECONOMY CLASS")) return "Economy Class";
        return "Business Class"; // default for Roame emails
    }

    private String extractAirline(String airlineAndCabin, String cabin) {
        int idx = airlineAndCabin.toUpperCase().indexOf(cabin.toUpperCase());
        return idx > 0 ? toTitleCase(airlineAndCabin.substring(0, idx).trim()) : toTitleCase(airlineAndCabin);
    }

    private String toTitleCase(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
