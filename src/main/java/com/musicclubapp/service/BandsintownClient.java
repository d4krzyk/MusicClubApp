package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Koncerty jednego wykonawcy z Bandsintown API v3 ({@code /artists/{nazwa}/events}). Bandsintown nie ma wyszukiwania
 * po miejscu - pyta sie o konkretnych wykonawcow, wiec pytamy o tych, ktorych ludzie u nas lubia.
 *
 * <p>Klucz ({@code app_id}) wydaje Bandsintown na pisemna prosbe i tylko do uzytku zgodnego z ich warunkami (podpis
 * "Bandsintown" przy danych, odnosnik do wydarzenia). Bez klucza zrodlo jest wylaczone.</p>
 */
@Service
public class BandsintownClient {

    private static final Logger log = LoggerFactory.getLogger(BandsintownClient.class);

    private final RestClient restClient;
    private final String appId;
    private final String api;

    public BandsintownClient(@Value("${app.events.bandsintown.app-id:}") String appId,
                             @Value("${app.events.bandsintown.base-url:https://rest.bandsintown.com}") String api,
                             @Value("${app.events.bandsintown.timeout-ms:10000}") int timeoutMs) {
        this.appId = appId == null ? "" : appId.trim();
        this.api = api;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() {
        return !appId.isEmpty();
    }

    /**
     * Nadchodzace koncerty wykonawcy. Nieznany wykonawca = pusta lista; awaria = wyjatek z opisem bez klucza
     * (import pomija wtedy tego wykonawce i idzie dalej).
     */
    public List<ExternalEvent> artistEvents(String artist) {
        if (!available()) {
            throw new IllegalStateException("Brak klucza Bandsintown");
        }
        String url = UriComponentsBuilder.fromUriString(api)
            .pathSegment("artists", nazwaWAdresie(artist), "events")
            .queryParam("app_id", appId)
            .queryParam("date", "upcoming")
            .build(true)
            .toUriString();
        JsonNode response;
        try {
            response = restClient.get().uri(java.net.URI.create(url)).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return List.of();
            }
            throw new IllegalStateException("Bandsintown odmowil: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new IllegalStateException("Bandsintown nie odpowiada: " + TicketmasterClient.opisBledu(e, appId));
        }
        if (response == null || !response.isArray()) {
            // {"errorMessage": "[NotFound] The artist was not found"} albo inne niespodzianki - nic nie wiemy
            if (response != null && response.has("errorMessage")) {
                log.debug("Bandsintown o '{}': {}", artist, response.path("errorMessage").asText());
            }
            return List.of();
        }
        List<ExternalEvent> wynik = new ArrayList<>();
        for (JsonNode e : response) {
            ExternalEvent event = read(e, artist);
            if (event != null) {
                wynik.add(event);
            }
        }
        return wynik;
    }

    /**
     * Bandsintown chce nazw z ukosnikiem, pytajnikiem i gwiazdka zakodowanych podwojnie (tak mowi ich dokumentacja:
     * "/" jako %252F) - zwykle kodowanie rozbiloby adres na dodatkowe segmenty.
     */
    static String nazwaWAdresie(String artist) {
        String zakodowana = UriComponentsBuilder.newInstance().pathSegment(artist.strip()).build()
            .encode().toUriString().substring(1);
        return zakodowana.replace("%2F", "%252F").replace("?", "%253F").replace("%3F", "%253F")
            .replace("*", "%252A").replace("%22", "%27C");
    }

    static ExternalEvent read(JsonNode e, String artist) {
        String id = text(e, "id");
        String kiedy = text(e, "datetime") != null ? text(e, "datetime") : text(e, "starts_at");
        if (id == null || kiedy == null) {
            return null;
        }
        LocalDateTime czas;
        try {
            czas = LocalDateTime.parse(kiedy.length() > 19 ? kiedy.substring(0, 19) : kiedy);
        } catch (DateTimeParseException ex) {
            return null;
        }
        JsonNode venue = e.path("venue");
        List<String> sklad = new ArrayList<>();
        for (JsonNode n : e.path("lineup")) {
            String nazwa = TicketmasterClient.clean(n.asText(null), 200);
            if (nazwa != null && sklad.size() < 10) {
                sklad.add(nazwa);
            }
        }
        if (sklad.isEmpty()) {
            sklad.add(artist.strip());
        }
        String tytul = TicketmasterClient.clean(text(e, "title"), 300);
        String bilety = null;
        for (JsonNode o : e.path("offers")) {
            String adres = text(o, "url");
            if (adres != null && TicketmasterClient.safeLink(adres) && adres.length() <= 1000) {
                bilety = adres;
                break;
            }
        }
        String strona = text(e, "url");
        Double lat = liczba(text(venue, "latitude"));
        Double lon = liczba(text(venue, "longitude"));
        return new ExternalEvent(EventSource.BANDSINTOWN, id,
            strona != null && TicketmasterClient.safeLink(strona) && strona.length() <= 1000 ? strona : null,
            tytul != null ? tytul : String.join(" + ", sklad.subList(0, Math.min(3, sklad.size()))),
            EventStatus.SCHEDULED,
            czas.toLocalDate(),
            czas.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? null : czas.toLocalTime(),
            TicketmasterClient.clean(text(venue, "name"), 200),
            TicketmasterClient.clean(text(venue, "city"), 100),
            Countries.isoFromEnglishName(text(venue, "country")),
            TicketmasterClient.clean(text(venue, "street_address"), 300),
            lat != null && lon != null ? lat : null,
            lat != null && lon != null ? lon : null,
            bilety,
            null,
            TicketmasterClient.clean(text(e, "description"), 1000),
            sklad);
    }

    private static Double liczba(String value) {
        try {
            double d = Double.parseDouble(value);
            return Double.isFinite(d) ? d : null;
        } catch (NullPointerException | NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
    }
}
