package com.musicclubapp.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Web Push - podpis VAPID (RFC 8292)")
class VapidTest {

    private static final Pattern NAGLOWEK = Pattern.compile("vapid t=([^.]+)\\.([^.]+)\\.([^,]+), k=(.+)");

    private final KeyPair para = P256.generate();
    private final String publiczny = P256.base64(P256.encode((ECPublicKey) para.getPublic()));
    private final String prywatny = P256.base64(P256.encode((ECPrivateKey) para.getPrivate()));

    @Test
    @DisplayName("naglowek: JWT podpisany kluczem serwera, odbiorca to sama usluga push, waznosc 12 h")
    void signsJwt() throws Exception {
        Vapid vapid = new Vapid(publiczny, prywatny, "mailto:admin@example.com");
        Instant teraz = Instant.parse("2026-09-29T10:00:00Z");

        String naglowek = vapid.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc:def"), teraz);

        Matcher m = NAGLOWEK.matcher(naglowek);
        assertThat(m.matches()).as(naglowek).isTrue();
        assertThat(m.group(4)).isEqualTo(publiczny);

        ObjectMapper json = new ObjectMapper();
        JsonNode glowa = json.readTree(Base64.getUrlDecoder().decode(m.group(1)));
        JsonNode tresc = json.readTree(Base64.getUrlDecoder().decode(m.group(2)));
        assertThat(glowa.get("alg").asText()).isEqualTo("ES256");
        assertThat(tresc.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(tresc.get("sub").asText()).isEqualTo("mailto:admin@example.com");
        assertThat(tresc.get("exp").asLong()).isEqualTo(teraz.plusSeconds(12 * 3600).getEpochSecond());

        Signature weryfikacja = Signature.getInstance("SHA256withECDSAinP1363Format");
        weryfikacja.initVerify(para.getPublic());
        weryfikacja.update((m.group(1) + "." + m.group(2)).getBytes(StandardCharsets.US_ASCII));
        assertThat(weryfikacja.verify(Base64.getUrlDecoder().decode(m.group(3)))).isTrue();
    }

    @Test
    @DisplayName("port w adresie uslugi trafia do odbiorcy tokenu")
    void audienceKeepsPort() throws Exception {
        String naglowek = new Vapid(publiczny, prywatny, "https://musicclub.example.com")
            .authorization(URI.create("http://localhost:8766/push/1"), Instant.now());
        Matcher m = NAGLOWEK.matcher(naglowek);
        assertThat(m.matches()).isTrue();
        assertThat(new ObjectMapper().readTree(Base64.getUrlDecoder().decode(m.group(2))).get("aud").asText())
            .isEqualTo("http://localhost:8766");
    }

    @Test
    @DisplayName("klucze z dwoch roznych generowan - blad od razu, a nie ciche odrzucanie wiadomosci")
    void rejectsMismatchedPair() {
        String obcyPrywatny = P256.base64(P256.encode((ECPrivateKey) P256.generate().getPrivate()));

        assertThatThrownBy(() -> new Vapid(publiczny, obcyPrywatny, "mailto:a@example.com"))
            .hasMessageContaining("nie pasuje");
    }

    @Test
    @DisplayName("kontakt musi byc mailto: albo https://")
    void rejectsBadSubject() {
        assertThatThrownBy(() -> new Vapid(publiczny, prywatny, "admin@example.com"))
            .hasMessageContaining("VAPID_SUBJECT");
        assertThatThrownBy(() -> new Vapid(publiczny, prywatny, "http://example.com"))
            .hasMessageContaining("VAPID_SUBJECT");
    }
}
