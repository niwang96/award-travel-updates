package com.awardtravelupdates.service;

import com.awardtravelupdates.accessor.DiscordAccessor;
import com.awardtravelupdates.model.FlightDeal;
import com.awardtravelupdates.model.SummaryResult;
import com.awardtravelupdates.model.SummaryUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotificationService {

    private static final LinkedHashMap<String, String> TOPICS = new LinkedHashMap<>();
    static {
        TOPICS.put("credit_cards", "Credit Cards");
        TOPICS.put("flights",      "Flights");
        TOPICS.put("hotels",       "Hotels");
        TOPICS.put("lounges",      "Lounges");
        TOPICS.put("status",       "Status & Elite");
        TOPICS.put("deals",        "Deals");
    }

    private static final int DISCORD_MAX_CHARS = 2000;
    private static final int MAX_TEXT_LENGTH = 80;

    private static final Map<String, String> CITY_TO_IATA = Map.ofEntries(
            // North America
            entry("New York", "NYC"), entry("Los Angeles", "LAX"), entry("San Francisco", "SFO"),
            entry("San Francico", "SFO"), // typo from parser
            entry("Chicago", "CHI"), entry("Dallas", "DFW"), entry("Houston", "IAH"),
            entry("Miami", "MIA"), entry("Boston", "BOS"), entry("Washington", "WAS"), entry("Washington Dc", "WAS"),
            entry("Seattle", "SEA"), entry("Atlanta", "ATL"), entry("Denver", "DEN"),
            entry("Phoenix", "PHX"), entry("Las Vegas", "LAS"), entry("Honolulu", "HNL"),
            entry("Minneapolis", "MSP"), entry("Detroit", "DTW"), entry("Philadelphia", "PHL"),
            entry("Portland", "PDX"), entry("Salt Lake City", "SLC"), entry("Austin", "AUS"),
            entry("San Diego", "SAN"), entry("Orlando", "MCO"), entry("Charlotte", "CLT"),
            entry("Newark", "EWR"), entry("Toronto", "YTO"), entry("Vancouver", "YVR"),
            entry("Montreal", "YMQ"), entry("Calgary", "YYC"), entry("Mexico City", "MEX"),
            entry("Cancun", "CUN"), entry("Lima", "LIM"), entry("Bogota", "BOG"),
            entry("Santiago", "SCL"), entry("Buenos Aires", "BUE"), entry("Sao Paulo", "SAO"),
            entry("Rio de Janeiro", "RIO"),
            // Europe
            entry("London", "LON"), entry("Paris", "PAR"), entry("Frankfurt", "FRA"),
            entry("Amsterdam", "AMS"), entry("Zurich", "ZRH"), entry("Munich", "MUC"),
            entry("Rome", "ROM"), entry("Madrid", "MAD"), entry("Barcelona", "BCN"),
            entry("Istanbul", "IST"), entry("Dublin", "DUB"), entry("Lisbon", "LIS"),
            entry("Vienna", "VIE"), entry("Brussels", "BRU"), entry("Copenhagen", "CPH"),
            entry("Stockholm", "STO"), entry("Oslo", "OSL"), entry("Helsinki", "HEL"),
            entry("Warsaw", "WAW"), entry("Prague", "PRG"), entry("Budapest", "BUD"),
            entry("Athens", "ATH"),
            // Middle East & Africa
            entry("Dubai", "DXB"), entry("Doha", "DOH"), entry("Abu Dhabi", "AUH"),
            entry("Tel Aviv", "TLV"), entry("Nairobi", "NBO"), entry("Cairo", "CAI"),
            entry("Johannesburg", "JNB"), entry("Lagos", "LOS"), entry("Casablanca", "CMN"),
            entry("Addis Ababa", "ADD"),
            // Asia & Pacific
            entry("Tokyo", "TYO"), entry("Osaka", "OSA"), entry("Nagoya", "NGO"),
            entry("Sapporo", "CTS"), entry("Fukuoka", "FUK"), entry("Seoul", "SEL"),
            entry("Beijing", "BJS"), entry("Shanghai", "PVG"), entry("Guangzhou", "CAN"),
            entry("Chengdu", "CTU"), entry("Hong Kong", "HKG"), entry("Taipei", "TPE"),
            entry("Singapore", "SIN"), entry("Bangkok", "BKK"), entry("Kuala Lumpur", "KUL"),
            entry("Jakarta", "JKT"), entry("Manila", "MNL"), entry("Mumbai", "BOM"),
            entry("Delhi", "DEL"), entry("Sydney", "SYD"), entry("Melbourne", "MEL"),
            entry("Auckland", "AKL"),
            entry("Shannon", "SNN"), entry("Maldives", "MLE"), entry("Ho Chi Minh", "SGN")
    );

    private final CombinedSummaryService combinedSummaryService;
    private final EmailDealsSummaryService emailDealsSummaryService;
    private final DiscordAccessor discordAccessor;

    public void send() {
        Map<String, SummaryResult> data = combinedSummaryService.getSummaries();
        List<String> messages = formatMessages(data);

        List<FlightDeal> allDeals = emailDealsSummaryService.getDeals();
        long latestEmail = allDeals.stream().mapToLong(FlightDeal::dateFound).max().orElse(0);
        List<FlightDeal> deals = allDeals.stream()
                .filter(d -> d.dateFound() == latestEmail)
                .toList();
        messages.addAll(formatDealsMessages(deals));

        log.info("Sending {} Discord message(s)", messages.size());
        for (String message : messages) {
            discordAccessor.sendMessage(message);
        }
        log.info("Discord notification sent successfully");
    }

    private List<String> formatMessages(Map<String, SummaryResult> data) {
        List<SummaryUpdate> allUpdates = data.values().stream()
                .flatMap(result -> result.updates().stream())
                .toList();

        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Map.Entry<String, String> entry : TOPICS.entrySet()) {
            List<SummaryUpdate> topicUpdates = allUpdates.stream()
                    .filter(u -> entry.getKey().equals(u.topic()))
                    .toList();
            if (topicUpdates.isEmpty()) continue;

            StringBuilder section = new StringBuilder();
            section.append("**").append(entry.getValue()).append("**\n");
            for (SummaryUpdate update : topicUpdates) {
                String text = truncate(update.text());
                String bullet = update.source() != null && !update.source().isBlank()
                        ? "• [" + text + "](<" + update.source() + ">)\n"
                        : "• " + text + "\n";
                section.append(bullet);
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

    private List<String> formatDealsMessages(List<FlightDeal> deals) {
        if (deals.isEmpty()) return List.of();

        List<String> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder("**Flight Deals**\n");

        for (FlightDeal deal : deals) {
            String summary = String.format("%,d pts | %s | %s | %s → %s | %s | %s",
                    deal.points(), deal.airline(), deal.cabin(),
                    deal.origin(), deal.destination(),
                    deal.flightDate(), deal.bookingProgram());
            Optional<String> url = buildRoameUrl(deal);
            String bullet = url.isPresent()
                    ? "• [" + summary + "](<" + url.get() + ">)\n"
                    : "• " + summary + "\n";
            if (sb.length() + bullet.length() > DISCORD_MAX_CHARS) {
                messages.add(sb.toString().trim());
                sb = new StringBuilder("**Flight Deals (cont.)**\n");
            }
            sb.append(bullet);
        }
        messages.add(sb.toString().trim());
        return messages;
    }

    private Optional<String> buildRoameUrl(FlightDeal deal) {
        String originCode = CITY_TO_IATA.get(deal.origin());
        String destCode = CITY_TO_IATA.get(deal.destination());
        if (originCode == null || destCode == null) return Optional.empty();

        String date = roameDate(deal.flightDate());
        if (date == null) return Optional.empty();

        String fareClass = switch (deal.cabin()) {
            case "First Class" -> "FIRST";
            case "Economy Class" -> "ECONOMY";
            default -> "BUSINESS";
        };
        String searchClass = "Economy Class".equals(deal.cabin()) ? "ECO" : "PREM";

        String url = "https://roame.travel/search?origin=" + originCode
                + "&originType=airport&destination=" + destCode
                + "&destinationType=airport&departureDate=" + date
                + "&endDepartureDate=" + date
                + "&pax=1&searchClass=" + searchClass
                + "&fareClasses=" + fareClass
                + "&isSkyview=false&flexibleDates=1";
        return Optional.of(url);
    }

    private String roameDate(String flightDate) {
        // Convert "M/DD/YYYY" → "YYYY-MM-DD"
        try {
            String[] parts = flightDate.split("/");
            return String.format("%s-%02d-%02d", parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }
}
