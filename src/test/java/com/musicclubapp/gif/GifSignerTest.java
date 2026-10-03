package com.musicclubapp.gif;

import com.musicclubapp.entity.GifAttachment;
import com.musicclubapp.error.InvalidGifException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GifSigner - podpisane wyniki wyszukiwania")
class GifSignerTest {

    private final GifSigner signer = new GifSigner("tajny-klucz-testowy");
    private final GifItem item = new GifItem("1", "Kot na pianinie", "https://cdn.example/a.gif",
        "https://cdn.example/a-m.gif", 320, 180);

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("token z wyszukiwania wraca jako ten sam GIF")
    void roundTrip() {
        GifAttachment gif = signer.verify(signer.sign(item));
        assertThat(gif.getUrl()).isEqualTo("https://cdn.example/a.gif");
        assertThat(gif.getPreviewUrl()).isEqualTo("https://cdn.example/a-m.gif");
        assertThat(gif.getWidth()).isEqualTo(320);
        assertThat(gif.getHeight()).isEqualTo(180);
        assertThat(gif.getTitle()).isEqualTo("Kot na pianinie");
    }

    @Test
    @DisplayName("zmiana jakiegokolwiek znaku w danych albo w podpisie unieważnia token")
    void tamperedTokenIsRejected() {
        String token = signer.sign(item);
        int kropka = token.indexOf('.');
        String dane = token.substring(0, kropka);
        String podpis = token.substring(kropka + 1);

        // inny adres z tym samym podpisem
        String inne = base64("{\"u\":\"https://evil.example/pixel.gif\",\"p\":\"https://cdn.example/a-m.gif\",\"w\":1,\"h\":1,\"t\":\"\"}");
        assertThatThrownBy(() -> signer.verify(inne + "." + podpis)).isInstanceOf(InvalidGifException.class);
        // zmieniony pierwszy znak podpisu
        char zamiast = podpis.charAt(0) == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> signer.verify(dane + "." + zamiast + podpis.substring(1))).isInstanceOf(InvalidGifException.class);
        // brak podpisu
        assertThatThrownBy(() -> signer.verify(dane + ".")).isInstanceOf(InvalidGifException.class);
        assertThatThrownBy(() -> signer.verify(dane)).isInstanceOf(InvalidGifException.class);
    }

    @Test
    @DisplayName("token podpisany innym kluczem nie przechodzi")
    void otherKey() {
        String token = new GifSigner("inny-klucz").sign(item);
        assertThatThrownBy(() -> signer.verify(token)).isInstanceOf(InvalidGifException.class);
    }

    @Test
    @DisplayName("smieci, pusty i za dlugi token - odrzucone")
    void garbage() {
        assertThatThrownBy(() -> signer.verify(null)).isInstanceOf(InvalidGifException.class);
        assertThatThrownBy(() -> signer.verify("")).isInstanceOf(InvalidGifException.class);
        assertThatThrownBy(() -> signer.verify("abc")).isInstanceOf(InvalidGifException.class);
        assertThatThrownBy(() -> signer.verify(".")).isInstanceOf(InvalidGifException.class);
        assertThatThrownBy(() -> signer.verify("a".repeat(2001))).isInstanceOf(InvalidGifException.class);
    }

    @Test
    @DisplayName("nawet poprawnie podpisany adres musi byc https (albo petla zwrotna)")
    void schemeIsCheckedEvenWhenSigned() {
        GifItem zly = new GifItem("2", "", "javascript:alert(1)", "https://cdn.example/a-m.gif", 1, 1);
        assertThatThrownBy(() -> signer.verify(signer.sign(zly))).isInstanceOf(InvalidGifException.class);
        GifItem http = new GifItem("3", "", "http://cdn.example/a.gif", "https://cdn.example/a-m.gif", 1, 1);
        assertThatThrownBy(() -> signer.verify(signer.sign(http))).isInstanceOf(InvalidGifException.class);
        // podglad sprawdzamy tak samo jak glowny adres
        GifItem zlyPodglad = new GifItem("5", "", "https://cdn.example/a.gif", "http://cdn.example/a-m.gif", 1, 1);
        assertThatThrownBy(() -> signer.verify(signer.sign(zlyPodglad))).isInstanceOf(InvalidGifException.class);
        // adres dluzszy niz kolumna w bazie (500) nie przejdzie, nawet podpisany
        GifItem zaDlugi = new GifItem("6", "", "https://cdn.example/" + "a".repeat(481) + ".gif", "https://cdn.example/a-m.gif", 1, 1);
        assertThatThrownBy(() -> signer.verify(signer.sign(zaDlugi))).isInstanceOf(InvalidGifException.class);
        GifItem petla = new GifItem("4", "", "http://127.0.0.1:9/a.gif", "http://localhost:9/a-m.gif", 1, 1);
        assertThat(signer.verify(signer.sign(petla)).getUrl()).isEqualTo("http://127.0.0.1:9/a.gif");
    }

    @Test
    @DisplayName("pseudonim jest stabilny, rozny dla roznych osob i nie zawiera wejscia")
    void pseudonym() {
        assertThat(signer.pseudonym(42L)).isEqualTo(signer.pseudonym(42L));
        assertThat(signer.pseudonym(42L)).isNotEqualTo(signer.pseudonym(43L));
        assertThat(signer.pseudonym(42L)).hasSize(16).doesNotContain("42x");
        assertThat(new GifSigner("inny").pseudonym(42L)).isNotEqualTo(signer.pseudonym(42L));
    }
}
