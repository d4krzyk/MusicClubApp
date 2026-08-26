package com.musicclubapp.validation;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.music.MusicKind;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy walidacji linku muzycznego.
 *
 * <p>To najwazniejsza zmiana tej rundy: <b>zly link ma ZATRZYMAC wysylke</b>,
 * a nie zostac po cichu polkniety. Kazdy test sprawdza takze, DO KTOREGO POLA
 * trafil komunikat - bez tego frontend nie wiedzialby, co podswietlic.</p>
 *
 * <p>Walidator uruchamiamy tu bezposrednio, bez podnoszenia calego Springa -
 * to zwykly test jednostkowy.</p>
 */
@DisplayName("PoprawnyLinkMuzyczny - blokada zlych linkow")
class PoprawnyLinkMuzycznyValidatorTest {

    private static ValidatorFactory fabryka;
    private static Validator validator;

    private static final String UTWOR = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT";
    private static final String ALBUM = "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT";

    @BeforeAll
    static void przygotuj() {
        fabryka = Validation.buildDefaultValidatorFactory();
        validator = fabryka.getValidator();
    }

    @AfterAll
    static void posprzataj() {
        fabryka.close();
    }

    /** Zwraca nazwy pol, przy ktorych pojawil sie blad. */
    private Set<String> bledneP0la(CreatePostRequest zadanie) {
        Set<ConstraintViolation<CreatePostRequest>> naruszenia = validator.validate(zadanie);
        return naruszenia.stream()
            .map(n -> n.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("post bez muzyki przechodzi")
    void bezMuzykiJestOk() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("poprawny utwor z momentem startu przechodzi")
    void poprawnyUtwor() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", UTWOR, MusicKind.TRACK, 70))).isEmpty();
    }

    @Test
    @DisplayName("BELKOT zamiast linku zatrzymuje wysylke")
    void belkotOdrzucony() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", "to nie jest link", MusicKind.TRACK, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("link do albumu przy wybranym rodzaju 'utwor' jest odrzucany")
    void niezgodnyRodzaj() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.TRACK, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("moment startu przy ALBUMIE jest odrzucany")
    void momentPrzyAlbumie() {
        /*
         * Formularz chowa to pole przy albumie, ale serwer nie moze na tym
         * polegac - zapytanie da sie wyslac z pominieciem przegladarki.
         */
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.ALBUM, 70)))
            .contains("musicStartSeconds");
    }

    @Test
    @DisplayName("album BEZ momentu startu przechodzi")
    void albumBezMomentu() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.ALBUM, null))).isEmpty();
    }

    @Test
    @DisplayName("link bez wybranego rodzaju zatrzymuje wysylke")
    void brakRodzaju() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", UTWOR, null, null)))
            .contains("musicKind");
    }

    @Test
    @DisplayName("wybrany rodzaj bez linku zatrzymuje wysylke")
    void brakLinku() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, MusicKind.TRACK, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("moment startu bez zadnego linku zatrzymuje wysylke")
    void momentBezLinku() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, null, 70)))
            .contains("musicStartSeconds");
    }

    @Test
    @DisplayName("link z YouTube przechodzi jako utwor")
    void youtubeJakoUtwor() {
        assertThat(bledneP0la(new CreatePostRequest(
            "tresc", "https://youtu.be/dQw4w9WgXcQ", MusicKind.TRACK, 42))).isEmpty();
    }

    @Test
    @DisplayName("YouTube wybrany jako ALBUM jest odrzucany")
    void youtubeNieJestAlbumem() {
        // Na YouTube album bywa playlista - to inny byt i nie udajemy, ze go umiemy
        assertThat(bledneP0la(new CreatePostRequest(
            "tresc", "https://youtu.be/dQw4w9WgXcQ", MusicKind.ALBUM, null)))
            .contains("musicUrl");
    }
}
