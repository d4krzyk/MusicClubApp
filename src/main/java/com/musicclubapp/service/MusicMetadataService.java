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

/**
 * Pobiera tytul i miniaturke nagrania przez <b>oEmbed</b>.
 *
 * <p><b>Dlaczego oEmbed, a nie zwykle API serwisu?</b> Bo jest publiczny -
 * nie wymaga klucza, tokenu ani rejestracji aplikacji. Dziala wiec dla
 * KAZDEGO uzytkownika, a nie tylko dla pieciu kont, do ktorych Spotify
 * ogranicza aplikacje w trybie deweloperskim.</p>
 *
 * <p><b>Pobieramy RAZ, przy dodawaniu posta.</b> Wynik ladzie w bazie razem
 * z postem. Gdybysmy pytali serwis przy kazdym wyswietleniu tablicy,
 * dwadziescia postow na stronie oznaczaloby dwadziescia zapytan do obcego
 * serwera - a tablica ladowalaby sie tak wolno, jak najwolniejsze z nich.</p>
 *
 * <p><b>Awaria serwisu NIE MOZE blokowac dodania posta.</b> Tytul i miniaturka
 * to ozdoba: odtwarzacz i tak pobiera sobie wszystko sam, bo {@code <iframe>}
 * laduje sie po stronie przegladarki. Dlatego kazdy blad konczy sie
 * zwroceniem pustych danych i wpisem w logu - nigdy wyjatkiem lecacym
 * do uzytkownika.</p>
 *
 * <p>Zapytanie idzie <b>z serwera</b>, nie z przegladarki - oEmbed Spotify
 * nie wysyla naglowkow CORS, wiec wywolanie z JavaScriptu i tak by sie
 * nie udalo.</p>
 */
@Service
public class MusicMetadataService {

    private static final Logger log = LoggerFactory.getLogger(MusicMetadataService.class);

    /** Tytul i miniaturka nagrania; oba pola moga byc puste. */
    public record Opis(String tytul, String miniaturka) {

        public static Opis pusty() {
            return new Opis(null, null);
        }
    }

    private final RestClient restClient;

    public MusicMetadataService(
            @Value("${app.music.oembed.timeout-ms:3000}") int timeoutMs) {

        /*
         * Krotki limit czasu jest tu kluczowy. Bez niego zawieszony serwer
         * Spotify blokowalby watek dodajacy post az do domyslnego limitu
         * systemowego - uzytkownik patrzylby w krecace sie kolko przez
         * kilkadziesiat sekund, po czym i tak dostalby blad.
         */
        SimpleClientHttpRequestFactory fabryka = new SimpleClientHttpRequestFactory();
        fabryka.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabryka.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
            .requestFactory(fabryka)
            .build();
    }

    /**
     * @return tytul i miniaturka albo {@link Opis#pusty()}, gdy serwis
     *         nie odpowiedzial lub odpowiedzial czyms nieoczekiwanym
     */
    public Opis pobierz(ParsedMusicLink link) {
        String adres = MusicEmbed.adresOEmbed(link.provider(), link.kind(), link.externalId());

        try {
            JsonNode odpowiedz = restClient.get()
                .uri(adres)
                .retrieve()
                .body(JsonNode.class);

            if (odpowiedz == null) {
                return Opis.pusty();
            }

            return new Opis(
                tekst(odpowiedz, "title"),
                tekst(odpowiedz, "thumbnail_url"));

        } catch (Exception e) {
            /*
             * Lapiemy WSZYSTKO celowo: brak sieci, blad 404 dla usunietego
             * utworu, przekroczony czas, niespodziewany format odpowiedzi.
             * Zaden z tych przypadkow nie jest powodem, zeby uzytkownik nie
             * mogl dodac posta.
             */
            log.warn("Nie udalo sie pobrac opisu nagrania ({}): {}", adres, e.getMessage());
            return Opis.pusty();
        }
    }

    private String tekst(JsonNode wezel, String pole) {
        JsonNode wartosc = wezel.get(pole);
        return wartosc == null || wartosc.isNull() ? null : wartosc.asText();
    }
}
