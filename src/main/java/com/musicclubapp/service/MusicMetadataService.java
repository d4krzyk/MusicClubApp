package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.musicclubapp.music.MusicEmbed;
import com.musicclubapp.music.ParsedMusicLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Pobiera tytul i miniaturke nagrania przez oEmbed. */
@Service
public class MusicMetadataService {

    private static final Logger log = LoggerFactory.getLogger(MusicMetadataService.class);

    /** Tytul i miniaturka nagrania; oba pola moga byc puste. */
    public record Metadata(String title, String thumbnailUrl) {

        public static Metadata empty() {
            return new Metadata(null, null);
        }
    }

    private final RestClient restClient;

    public MusicMetadataService(
            @Value("${app.music.oembed.timeout-ms:3000}") int timeoutMs) {

        /* Krotki limit czasu jest tu kluczowy. */
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();
    }

    public Metadata fetch(ParsedMusicLink link) {
        String url = MusicEmbed.oEmbedUrl(link.provider(), link.kind(), link.externalId());

        if (url == null) {
            /* Serwis nie wystawia oEmbed (Apple Music) - to nie jest blad. */
            return new Metadata(MusicEmbed.titleFromUrl(link.provider(), link.externalId()), null);
        }

        try {
            JsonNode response = restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                return Metadata.empty();
            }

            return new Metadata(
                text(response, "title"),
                text(response, "thumbnail_url"));

        } catch (Exception e) {
            /*
             * Lapiemy WSZYSTKO celowo: brak sieci, blad 404 dla usunietego utworu, przekroczony
             * czas, niespodziewany format odpowiedzi.
             */
            log.warn("Nie udalo sie pobrac opisu nagrania ({}): {}", url, e.getMessage());
            return Metadata.empty();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
