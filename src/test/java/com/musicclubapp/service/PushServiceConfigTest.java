package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Konfiguracja push i lista uslug, do ktorych serwer w ogole cos wysle. */
@DisplayName("Push - konfiguracja i lista uslug")
class PushServiceConfigTest {

    static final String PUB = PushFlowTest.class.getAnnotation(org.springframework.test.context.TestPropertySource.class)
        .properties()[0].split("=", 2)[1];
    static final String PRIV = PushFlowTest.class.getAnnotation(org.springframework.test.context.TestPropertySource.class)
        .properties()[1].split("=", 2)[1];

    private static PushService push(String pub, String priv, String subject, String publicUrl, String extra) {
        return new PushService(pub, priv, subject, publicUrl, extra, 60, null, null, null, Clock.systemUTC(), Runnable::run);
    }

    @Test
    @DisplayName("bez kluczy - push wylaczony, serwer startuje")
    void noKeysMeansOff() {
        assertThat(push("", "", "", "", "").enabled()).isFalse();
    }

    @Test
    @DisplayName("pol pary, zly klucz albo brak kontaktu - serwer nie wstaje, z podpowiedzia")
    void badConfigFailsFast() {
        assertThatThrownBy(() -> push(PUB, "", "mailto:a@b.pl", "", ""))
            .hasMessageContaining("VAPID_PUBLIC_KEY i VAPID_PRIVATE_KEY");
        assertThatThrownBy(() -> push(PUB, "zly", "mailto:a@b.pl", "", ""))
            .hasMessageStartingWith("Push:");
        assertThatThrownBy(() -> push(PUB, PRIV, "", "http://localhost:5173", ""))
            .hasMessageContaining("VAPID_SUBJECT");
        // Kontakt z adresu strony, gdy jest https
        assertThat(push(PUB, PRIV, "", "https://musicclub.example.com", "").enabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://fcm.googleapis.com/fcm/send/abc",
        "https://updates.push.services.mozilla.com/wpush/v2/abc",
        "https://web.push.apple.com/QK1abc",
        "https://wns2-db5p.notify.windows.com/w/?token=abc"
    })
    @DisplayName("uslugi push przegladarek - przyjete")
    void knownServices(String adres) {
        assertThat(push(PUB, PRIV, "mailto:a@b.pl", "", "").adres(adres)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://fcm.googleapis.com/fcm/send/abc",
        "https://fcm.googleapis.com.evil.example/x",
        "https://evilpush.apple.com/x",
        "https://user:pass@fcm.googleapis.com/x",
        "https://localhost/x",
        "http://127.0.0.1:8080/actuator",
        "https://10.0.0.5/x",
        "nie adres",
        "javascript:alert(1)"
    })
    @DisplayName("wszystko inne - odrzucone (serwer nie bedzie tam wysylal)")
    void everythingElseRejected(String adres) {
        assertThat(push(PUB, PRIV, "mailto:a@b.pl", "", "").adres(adres)).isNull();
    }

    @Test
    @DisplayName("lokalna udawana usluga - tylko dopisana recznie, http tylko dla localhost")
    void extraHosts() {
        PushService zDopiskiem = push(PUB, PRIV, "mailto:a@b.pl", "", "127.0.0.1, push.example.org");
        assertThat(zDopiskiem.adres("http://127.0.0.1:8766/push/1")).isNotNull();
        assertThat(zDopiskiem.adres("https://push.example.org/1")).isNotNull();
        assertThat(zDopiskiem.adres("http://push.example.org/1")).isNull();
    }
}
