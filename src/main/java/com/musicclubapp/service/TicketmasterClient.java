package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Czytanie koncertow z Ticketmaster Discovery API.
 *
 * Ta klasa tylko pyta i tlumaczy odpowiedz na nasze rekordy. Co z nimi
 * zrobic - zapisac, podmienic, usunac - decyduje {@link EventImportService}.
 */
@Service
public class TicketmasterClient {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterClient.class);

    /**
     * Ticketmaster nie odda wiecej niz tyle wynikow dla jednego zapytania,
     * niezaleznie od tego, ile jest stron ("size * page < 1000"). Kto chce
     * wiecej, musi zawezic zapytanie - np. do krotszego okresu.
     */
    public static final int MAX_WYNIKOW_ZAPYTANIA = 1000;

    /** Najwiecej, ile Ticketmaster oddaje na jednej stronie. */
    public static final int ROZMIAR_STRONY = 200;

    /** Szerokosc zdjecia na strone wydarzenia i na liste. */
    private static final int SZEROKOSC_DUZEGO = 1024;
    private static final int SZEROKOSC_MINIATURY = 640;

    /** Opisy bywaja bardzo dlugie - wiecej i tak nikt nie przeczyta na telefonie. */
    private static final int MAX_OPIS = 4000;

    /** Ticketmaster chce czasu w UTC, bez ulamkow sekund: 2026-09-29T00:00:00Z. */
    private static final DateTimeFormatter FORMAT_CZASU = DateTimeFormatter.ISO_INSTANT;

    private final RestClient restClient;
    private final String key;
    private final String api;

    public TicketmasterClient(
            @Value("${app.ticketmaster.api-key:}") String key,
            @Value("${app.ticketmaster.base-url:https://app.ticketmaster.com/discovery/v2}") String api,
            @Value("${app.ticketmaster.timeout-ms:15000}") int timeoutMs) {

        this.key = key == null ? "" : key.trim();
        this.api = api;

        /*
         * Dluzszy limit niz przy Deezerze: strona z dwustoma wydarzeniami to
         * okolo megabajta JSON-a, a nikt tu nie czeka z palcem nad ekranem -
         * import idzie w tle.
         */
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        if (this.key.isEmpty()) {
            log.info("Klucz Ticketmastera nie jest ustawiony (app.ticketmaster.api-key) - "
                + "zakladka Wydarzenia bedzie pusta.");
        }
    }

    /** Czy w ogole mozna pytac (czy jest klucz). */
    public boolean available() {
        return !key.isEmpty();
    }

    /** Wykonawca ze skladu. */
    public record Performer(String externalId, String name) { }

    /** Jedno wydarzenie w postaci, ktora rozumie reszta aplikacji. */
    public record Event(
        String externalId,
        String name,
        LocalDate date,
        LocalTime time,
        EventStatus status,
        String description,
        String ticketUrl,
        String imageUrl,
        String thumbUrl,
        String venueExternalId,
        String venueName,
        String city,
        String address,
        Double latitude,
        Double longitude,
        String genre,
        String subGenre,
        List<Performer> performers
    ) { }

    /** Jedna strona wynikow. */
    public record Page(List<Event> events, int number, int totalPages, long totalElements) {

        public boolean last() {
            return number + 1 >= totalPages;
        }
    }

    /**
     * Wydarzenia muzyczne w danym kraju, zaczynajace sie miedzy {@code from} a {@code to}.
     *
     * @throws IllegalStateException gdy Ticketmaster odmowil albo nie odpowiedzial -
     *                               import ma wtedy przerwac i nic nie usuwac
     */
    public Page events(String countryCode, Instant from, Instant to, int page) {
        if (!available()) {
            throw new IllegalStateException("Brak klucza Ticketmastera");
        }

        String url = UriComponentsBuilder.fromUriString(api + "/events.json")
            .queryParam("countryCode", countryCode)
            .queryParam("classificationName", "music")
            .queryParam("startDateTime", FORMAT_CZASU.format(from.truncatedTo(ChronoUnit.SECONDS)))
            .queryParam("endDateTime", FORMAT_CZASU.format(to.truncatedTo(ChronoUnit.SECONDS)))
            .queryParam("sort", "date,asc")
            .queryParam("size", ROZMIAR_STRONY)
            .queryParam("page", page)
            .queryParam("apikey", key)
            .build()
            .toUriString();

        /*
         * Oryginalnego wyjatku celowo NIE dolaczamy jako przyczyny. Spring
         * wpisuje do jego tresci pelny adres zapytania - razem z kluczem - wiec
         * pierwsze zerwane polaczenie wypisaloby klucz do logow serwera.
         * Sam kod i komunikat wystarczaja: 401 to zly klucz, 429 to limit.
         */
        JsonNode response;
        try {
            response = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Ticketmaster odmowil: HTTP " + e.getStatusCode().value()
                + " " + faultstring(e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new IllegalStateException("Ticketmaster nie odpowiada: " + e.getClass().getSimpleName());
        }

        if (response == null) {
            throw new IllegalStateException("Ticketmaster oddal pusta odpowiedz");
        }

        /* Zly klucz przychodzi czasem jako 200 z polem "fault" w tresci. */
        if (response.has("fault")) {
            throw new IllegalStateException("Ticketmaster odmowil: "
                + text(response.path("fault"), "faultstring"));
        }

        List<Event> events = new ArrayList<>();
        for (JsonNode e : response.path("_embedded").path("events")) {
            Event event = read(e);
            if (event != null) {
                events.add(event);
            }
        }

        JsonNode info = response.path("page");
        return new Page(
            events,
            info.path("number").asInt(page),
            info.path("totalPages").asInt(0),
            info.path("totalElements").asLong(0));
    }

    /** Zamienia jedno wydarzenie z JSON-a na nasz rekord; null, gdy nie ma czego pokazac. */
    Event read(JsonNode e) {
        /* Wydarzenia testowe Ticketmastera - nie do pokazywania ludziom. */
        if (e.path("test").asBoolean(false)) {
            return null;
        }

        String id = text(e, "id");
        String name = clean(text(e, "name"), 300);
        JsonNode start = e.path("dates").path("start");
        LocalDate date = parseDate(text(start, "localDate"));

        /* Bez daty nie ma wydarzenia - "termin do ustalenia" nie zmiesci sie na liscie po dacie. */
        if (id == null || name == null || date == null) {
            return null;
        }

        boolean bezGodziny = start.path("timeTBA").asBoolean(false)
            || start.path("noSpecificTime").asBoolean(false);
        LocalTime time = bezGodziny ? null : parseTime(text(start, "localTime"));

        JsonNode venue = first(e.path("_embedded").path("venues"));
        JsonNode classification = primaryClassification(e.path("classifications"));

        List<Performer> performers = new ArrayList<>();
        for (JsonNode a : e.path("_embedded").path("attractions")) {
            String performerName = clean(text(a, "name"), 200);
            if (performerName != null) {
                performers.add(new Performer(clean(text(a, "id"), 64), performerName));
            }
        }

        /* "info" to zwykle zaproszenie od organizatora; "description" bywa zamiast niego. */
        String description = text(e, "info");
        if (description == null || description.isBlank()) {
            description = text(e, "description");
        }

        return new Event(
            clean(id, 64),
            name,
            date,
            time,
            status(text(e.path("dates").path("status"), "code")),
            clean(description, MAX_OPIS),
            clean(text(e, "url"), 1000),
            clean(image(e.path("images"), SZEROKOSC_DUZEGO), 1000),
            clean(image(e.path("images"), SZEROKOSC_MINIATURY), 1000),
            clean(text(venue, "id"), 64),
            clean(text(venue, "name"), 200),
            clean(text(venue.path("city"), "name"), 100),
            clean(text(venue.path("address"), "line1"), 300),
            coordinate(venue.path("location"), "latitude"),
            coordinate(venue.path("location"), "longitude"),
            genreName(classification.path("genre")),
            genreName(classification.path("subGenre")),
            performers);
    }

    /**
     * Wybiera zdjecie o proporcjach 16:9, najmniejsze, ktore ma co najmniej
     * zadana szerokosc.
     *
     * Ticketmaster podaje to samo zdjecie w kilkunastu rozmiarach i proporcjach.
     * "fallback" oznacza zastepcza grafike kategorii, a nie zdjecie tego
     * wydarzenia - bierzemy ja tylko wtedy, gdy nic innego nie ma.
     */
    static String image(JsonNode images, int width) {
        JsonNode best = null;
        for (boolean tylkoWlasne : new boolean[] {true, false}) {
            for (JsonNode img : images) {
                if (tylkoWlasne && img.path("fallback").asBoolean(false)) {
                    continue;
                }
                if (!"16_9".equals(img.path("ratio").asText())) {
                    continue;
                }
                best = better(best, img, width);
            }
            if (best != null) {
                return text(best, "url");
            }
        }

        /* Nie ma nic 16:9 - bierzemy cokolwiek. */
        for (JsonNode img : images) {
            best = better(best, img, width);
        }
        return best == null ? null : text(best, "url");
    }

    /** Ktore z dwoch zdjec jest blizej zadanej szerokosci - od gory, a gdy sie nie da, najwieksze. */
    private static JsonNode better(JsonNode current, JsonNode candidate, int width) {
        if (text(candidate, "url") == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        int a = current.path("width").asInt(0);
        int b = candidate.path("width").asInt(0);
        boolean aWystarcza = a >= width;
        boolean bWystarcza = b >= width;

        if (aWystarcza && bWystarcza) {
            return b < a ? candidate : current;
        }
        if (aWystarcza != bWystarcza) {
            return bWystarcza ? candidate : current;
        }
        return b > a ? candidate : current;
    }

    /** Klasyfikacja oznaczona jako glowna, a gdy takiej nie ma - pierwsza. */
    private static JsonNode primaryClassification(JsonNode classifications) {
        for (JsonNode c : classifications) {
            if (c.path("primary").asBoolean(false)) {
                return c;
            }
        }
        return first(classifications);
    }

    /** Ticketmaster wpisuje "Undefined", gdy gatunku nie zna - to nie jest gatunek. */
    private static String genreName(JsonNode node) {
        String name = clean(text(node, "name"), 60);
        return name == null || "undefined".equalsIgnoreCase(name) || "other".equalsIgnoreCase(name)
            ? null
            : name;
    }

    static EventStatus status(String code) {
        if (code == null) {
            return EventStatus.SCHEDULED;
        }
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "cancelled", "canceled" -> EventStatus.CANCELLED;
            case "postponed" -> EventStatus.POSTPONED;
            case "rescheduled" -> EventStatus.RESCHEDULED;
            default -> EventStatus.SCHEDULED;
        };
    }

    private static Double coordinate(JsonNode location, String field) {
        String value = text(location, field);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Przycina do dlugosci kolumny i usuwa biale znaki z brzegow ("Łódź " z Ticketmastera). */
    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).strip();
    }

    private static JsonNode first(JsonNode array) {
        return array.isArray() && !array.isEmpty()
            ? array.get(0)
            : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
    }

    /** Wyciaga sam komunikat bledu z odpowiedzi - bez reszty tresci. */
    private static String faultstring(String body) {
        if (body == null) {
            return "";
        }
        int i = body.indexOf("\"faultstring\"");
        if (i < 0) {
            return "";
        }
        int start = body.indexOf('"', body.indexOf(':', i) + 1);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        return start < 0 || end < 0 ? "" : body.substring(start + 1, end);
    }
}
