package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Wyszukiwarka artystow i utworow oparta na publicznym API Deezera. */
@Service
public class DeezerCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DeezerCatalogService.class);

    /** Gorna granica wynikow - zeby jedno zapytanie nie sciagalo setek pozycji. */
    private static final int MAX_RESULTS = 24;

    private final RestClient restClient;

    /** Adres API. */
    private final String api;

    public DeezerCatalogService(
            @Value("${app.music.deezer.base-url:https://api.deezer.com}") String api,
            @Value("${app.music.catalog.timeout-ms:4000}") int timeoutMs) {

        this.api = api;

        /*
         * Krotki limit czasu z tego samego powodu co przy oEmbed: zawieszony obcy serwer nie moze
         * blokowac naszego watku az do limitu systemowego.
         */
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Szuka artystow po nazwie. Zwraca pusta liste, gdy nic nie ma albo cos padlo. */
    public List<CatalogArtist> searchArtists(String phrase, int limit) {
        JsonNode data = fetchJson("/search/artist", phrase, limit);

        List<CatalogArtist> score = new ArrayList<>();
        for (JsonNode w : data) {
            score.add(new CatalogArtist(
                text(w, "id"),
                text(w, "name"),
                text(w, "picture_medium")));
        }
        return score;
    }

    /** Szuka utworow po nazwie (mozna podac tytul, wykonawce albo jedno i drugie). */
    public List<CatalogTrack> searchTracks(String phrase, int limit) {
        JsonNode data = fetchJson("/search/track", phrase, limit);

        List<CatalogTrack> score = new ArrayList<>();
        for (JsonNode w : data) {
            JsonNode artist = w.get("artist");
            JsonNode album = w.get("album");

            score.add(new CatalogTrack(
                text(w, "id"),
                text(w, "title"),
                artist == null ? null : text(artist, "name"),
                artist == null ? null : text(artist, "id"),
                album == null ? null : text(album, "cover_medium")));
        }
        return score;
    }

    /** Pierwszy artysta pasujacy do nazwy - uzywane przy imporcie z Last.fm. */
    public Optional<CatalogArtist> findArtist(String name) {
        return searchArtists(name, 1).stream().findFirst();
    }

    /** Pierwszy utwor pasujacy do "wykonawca tytul" - uzywane przy imporcie z Last.fm. */
    public Optional<CatalogTrack> findTrack(String artistName, String title) {
        return searchTracks(artistName + " " + title, 1).stream().findFirst();
    }

    /** Artysta pobrany po identyfikatorze - potwierdzenie, ze naprawde istnieje. */
    public Optional<CatalogArtist> fetchArtist(String externalId) {
        JsonNode w = fetchObject("/artist/", externalId);
        if (w == null) {
            return Optional.empty();
        }
        return Optional.of(new CatalogArtist(
            text(w, "id"), text(w, "name"), text(w, "picture_medium")));
    }

    /** Utwor pobrany po identyfikatorze - z tego samego powodu co wyzej. */
    public Optional<CatalogTrack> fetchTrack(String externalId) {
        JsonNode w = fetchObject("/track/", externalId);
        if (w == null) {
            return Optional.empty();
        }
        JsonNode artist = w.get("artist");
        JsonNode album = w.get("album");

        return Optional.of(new CatalogTrack(
            text(w, "id"),
            text(w, "title"),
            artist == null ? null : text(artist, "name"),
            artist == null ? null : text(artist, "id"),
            album == null ? null : text(album, "cover_medium")));
    }

    /** Pobiera pojedynczy obiekt po identyfikatorze. */
    private JsonNode fetchObject(String path, String externalId) {
        // Identyfikatory w Deezerze sa liczbami - sprawdzamy to, zanim
        // doklejymy cokolwiek do adresu
        if (externalId == null || !externalId.matches("\\d{1,20}")) {
            return null;
        }

        try {
            JsonNode response = restClient.get()
                .uri(api + path + externalId)
                .retrieve()
                .body(JsonNode.class);

            if (response == null || response.has("error") || !response.has("id")) {
                return null;
            }
            return response;

        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac {}{}: {}", path, externalId, e.getMessage());
            return null;
        }
    }

    /**
     * Wspolna czesc obu wyszukiwan: zapytanie, obsluga bledow i wyciagniecie tablicy data z
     * odpowiedzi.
     */
    private JsonNode fetchJson(String path, String phrase, int limit) {
        if (phrase == null || phrase.isBlank()) {
            return MissingNode.getInstance();
        }

        String url = UriComponentsBuilder.fromUriString(api + path)
            // builder sam koduje znaki specjalne - bez tego nazwa ze spacja
            // albo znakiem "&" rozsypalaby adres
            .queryParam("q", phrase.trim())
            .queryParam("limit", Math.max(1, Math.min(limit, MAX_RESULTS)))
            .build()
            .toUriString();

        try {
            JsonNode response = restClient.get().uri(url).retrieve().body(JsonNode.class);

            if (response == null) {
                return MissingNode.getInstance();
            }
            /*
             * Deezer sygnalizuje bledy POLEM "error" w tresci, a nie kodem HTTP - odpowiedz z
             * bledem ma status 200.
             */
            if (response.has("error")) {
                log.warn("Deezer odmowil ({}): {}", path, response.get("error"));
                return MissingNode.getInstance();
            }

            JsonNode data = response.get("data");
            return data == null ? MissingNode.getInstance() : data;

        } catch (Exception e) {
            // Brak sieci, przekroczony czas, nieoczekiwany format - zaden
            // z tych przypadkow nie jest powodem, zeby wywrocic strone profilu
            log.warn("Nie udalo sie odpytac Deezera ({}): {}", path, e.getMessage());
            return MissingNode.getInstance();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
