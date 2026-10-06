package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Koncerty w okolicy punktu z Songkick API v3 ({@code /events.json?location=geo:lat,lng}). Songkick wydaje klucze
 * tylko partnerom (platnie); bez klucza zrodlo jest wylaczone.
 */
@Service
public class SongkickClient {

    /** Najwiecej, ile Songkick oddaje na stronie. */
    public static final int ROZMIAR_STRONY = 50;

    private final RestClient restClient;
    private final String key;
    private final String api;

    public SongkickClient(@Value("${app.events.songkick.api-key:}") String key,
                          @Value("${app.events.songkick.base-url:https://api.songkick.com/api/3.0}") String api,
                          @Value("${app.events.songkick.timeout-ms:10000}") int timeoutMs) {
        this.key = key == null ? "" : key.trim();
        this.api = api;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() {
        return !key.isEmpty();
    }

    /** Strona wynikow: wydarzenia i czy jest nastepna. */
    public record Page(List<ExternalEvent> events, boolean last) {
    }

    public Page eventsNear(double lat, double lon, LocalDate from, LocalDate to, int page) {
        if (!available()) {
            throw new IllegalStateException("Brak klucza Songkick");
        }
        String url = UriComponentsBuilder.fromUriString(api + "/events.json")
            .queryParam("apikey", key)
            .queryParam("location", String.format(Locale.ROOT, "geo:%.4f,%.4f", lat, lon))
            .queryParam("min_date", from.toString())
            .queryParam("max_date", to.toString())
            .queryParam("page", page)
            .queryParam("per_page", ROZMIAR_STRONY)
            .build()
            .toUriString();
        JsonNode response;
        try {
            response = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Songkick odmowil: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new IllegalStateException("Songkick nie odpowiada: " + TicketmasterClient.opisBledu(e, key));
        }
        JsonNode strona = response == null ? null : response.path("resultsPage");
        if (strona == null || !"ok".equals(strona.path("status").asText())) {
            String powod = strona == null ? "pusta odpowiedz" : strona.path("error").path("message").asText("nieznany blad");
            throw new IllegalStateException("Songkick odmowil: " + powod.replace(key, "***"));
        }
        List<ExternalEvent> wynik = new ArrayList<>();
        for (JsonNode e : strona.path("results").path("event")) {
            ExternalEvent event = read(e);
            if (event != null) {
                wynik.add(event);
            }
        }
        int wszystkich = strona.path("totalEntries").asInt(0);
        int numer = strona.path("page").asInt(page);
        int naStronie = strona.path("perPage").asInt(ROZMIAR_STRONY);
        return new Page(wynik, numer * naStronie >= wszystkich);
    }

    static ExternalEvent read(JsonNode e) {
        String id = text(e, "id");
        String data = text(e.path("start"), "date");
        if (id == null || data == null) {
            return null;
        }
        LocalDate dzien;
        LocalTime godzina = null;
        try {
            dzien = LocalDate.parse(data);
            String czas = text(e.path("start"), "time");
            if (czas != null) {
                godzina = LocalTime.parse(czas);
            }
        } catch (DateTimeParseException ex) {
            return null;
        }
        List<String> sklad = new ArrayList<>();
        for (JsonNode p : e.path("performance")) {
            String nazwa = TicketmasterClient.clean(text(p.path("artist"), "displayName") != null
                ? text(p.path("artist"), "displayName") : text(p, "displayName"), 200);
            if (nazwa != null && sklad.size() < 10) {
                sklad.add(nazwa);
            }
        }
        JsonNode venue = e.path("venue");
        String nazwa;
        if ("Festival".equalsIgnoreCase(text(e, "type"))) {
            String seria = text(e.path("series"), "displayName");
            nazwa = seria != null ? seria : bezDaty(text(e, "displayName"));
        } else {
            nazwa = sklad.isEmpty() ? bezDaty(text(e, "displayName")) : String.join(", ", sklad.subList(0, Math.min(3, sklad.size())));
        }
        String miasto = text(venue.path("metroArea"), "displayName");
        if (miasto == null) {
            String m = text(e.path("location"), "city");
            miasto = m == null ? null : m.split(",")[0];
        }
        String kraj = Countries.isoFromEnglishName(text(venue.path("metroArea").path("country"), "displayName"));
        Double lat = venue.path("lat").isNumber() ? venue.path("lat").asDouble() : null;
        Double lon = venue.path("lng").isNumber() ? venue.path("lng").asDouble() : null;
        String strona = text(e, "uri");
        String status = text(e, "status");
        return new ExternalEvent(EventSource.SONGKICK, id,
            strona != null && TicketmasterClient.safeLink(strona) && strona.length() <= 1000 ? strona : null,
            TicketmasterClient.clean(nazwa, 300),
            "cancelled".equals(status) ? EventStatus.CANCELLED : "postponed".equals(status) ? EventStatus.POSTPONED
                : EventStatus.SCHEDULED,
            dzien, godzina,
            TicketmasterClient.clean(text(venue, "displayName"), 200),
            TicketmasterClient.clean(miasto, 100),
            kraj, null,
            lat != null && lon != null ? lat : null,
            lat != null && lon != null ? lon : null,
            null, null, null, sklad);
    }

    /** "Kult at Torwar (October 17, 2026)" -> "Kult at Torwar". */
    static String bezDaty(String nazwa) {
        return nazwa == null ? null : nazwa.replaceAll("\\s*\\([^()]*\\)\\s*$", "").strip();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
    }
}
