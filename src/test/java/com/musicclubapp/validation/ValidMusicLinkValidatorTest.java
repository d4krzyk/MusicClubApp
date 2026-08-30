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

/** Testy walidacji linku muzycznego. */
@DisplayName("ValidMusicLink - blokada zlych linkow")
class ValidMusicLinkValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final String UTWOR = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT";
    private static final String ALBUM = "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT";

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** Zwraca nazwy pol, przy ktorych pojawil sie blad. */
    private Set<String> bledneP0la(CreatePostRequest payload) {
        Set<ConstraintViolation<CreatePostRequest>> naruszenia = validator.validate(payload);
        return naruszenia.stream()
            .map(n -> n.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("post bez muzyki przechodzi")
    void noMusicIsFine() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("poprawny utwor z momentem startu przechodzi")
    void validTrack() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", UTWOR, MusicKind.TRACK, 70, null))).isEmpty();
    }

    @Test
    @DisplayName("BELKOT zamiast linku zatrzymuje wysylke")
    void gibberishRejected() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", "to nie jest link", MusicKind.TRACK, null, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("link do albumu przy wybranym rodzaju 'utwor' jest odrzucany")
    void mismatchedKind() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.TRACK, null, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("moment startu przy ALBUMIE jest odrzucany")
    void startSecondsOnAlbum() {
        /*
         * Formularz chowa to pole przy albumie, ale serwer nie moze na tym polegac - zapytanie da
         * sie wyslac z pominieciem przegladarki.
         */
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.ALBUM, 70, null)))
            .contains("musicStartSeconds");
    }

    @Test
    @DisplayName("album BEZ momentu startu przechodzi")
    void albumWithoutStartSeconds() {
        assertThat(bledneP0la(
            new CreatePostRequest("tresc", ALBUM, MusicKind.ALBUM, null, null))).isEmpty();
    }

    @Test
    @DisplayName("link bez wybranego rodzaju zatrzymuje wysylke")
    void missingKind() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", UTWOR, null, null, null)))
            .contains("musicKind");
    }

    @Test
    @DisplayName("wybrany rodzaj bez linku zatrzymuje wysylke")
    void missingLink() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, MusicKind.TRACK, null, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("moment startu bez zadnego linku zatrzymuje wysylke")
    void startSecondsWithoutLink() {
        assertThat(bledneP0la(new CreatePostRequest("tresc", null, null, 70, null)))
            .contains("musicStartSeconds");
    }

    @Test
    @DisplayName("link z YouTube Music przechodzi jako utwor")
    void youtubeAsTrack() {
        assertThat(bledneP0la(new CreatePostRequest(
            "tresc", "https://music.youtube.com/watch?v=dQw4w9WgXcQ", MusicKind.TRACK, 42, null))).isEmpty();
    }

    @Test
    @DisplayName("YouTube Music wybrany jako ALBUM jest odrzucany")
    void youtubeIsNotAnAlbum() {
        // Na YouTube album bywa playlista - to inny byt i nie udajemy, ze go umiemy
        assertThat(bledneP0la(new CreatePostRequest(
            "tresc", "https://music.youtube.com/watch?v=dQw4w9WgXcQ", MusicKind.ALBUM, null, null)))
            .contains("musicUrl");
    }

    @Test
    @DisplayName("zwykly YouTube dostaje WLASNY komunikat, a nie ogolne 'nieznany serwis'")
    void plainYouTubeHasItsOwnMessage() {
        /* Sama blokada to za malo. */
        Set<ConstraintViolation<CreatePostRequest>> naruszenia = validator.validate(
            new CreatePostRequest(
                "tresc", "https://youtu.be/dQw4w9WgXcQ", MusicKind.TRACK, null, null));

        assertThat(naruszenia).hasSize(1);
        ConstraintViolation<CreatePostRequest> error = naruszenia.iterator().next();

        assertThat(error.getPropertyPath()).hasToString("musicUrl");
        /* Poza Springiem walidator nie ma skad wziac tlumaczen, wiec oddaje sam KLUCZ. */
        assertThat(error.getMessage()).isEqualTo("{validation.music.url.youtubeNotMusic}");
    }

    @Test
    @DisplayName("tekst, ktory nie jest zadnym linkiem, dostaje komunikat ogolny")
    void nonLinkGetsGeneralMessage() {
        Set<ConstraintViolation<CreatePostRequest>> naruszenia = validator.validate(
            new CreatePostRequest("tresc", "zupelnie cos innego", MusicKind.TRACK, null, null));

        assertThat(naruszenia).hasSize(1);
        // Tu podpowiedz o YouTube Music byloby myląca - nikt YouTube'a nie wklejal
        assertThat(naruszenia.iterator().next().getMessage())
            .isEqualTo("{validation.music.url.invalid}");
    }
}
