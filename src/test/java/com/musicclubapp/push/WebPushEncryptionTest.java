package com.musicclubapp.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Szyfrowanie tresci push porownane bajt w bajt z biblioteka referencyjna
 * http_ece (Martin Thomson - wspolautor RFC 8188 i 8291). Wzorzec powstal
 * z tych samych kluczy i soli; ta sama biblioteka go odszyfrowuje.
 */
@DisplayName("Web Push - szyfrowanie aes128gcm (RFC 8291)")
class WebPushEncryptionTest {

    static final String NADAWCA_PRYWATNY = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String NADAWCA_PUBLICZNY =
        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String ODBIORCA_PUBLICZNY =
        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String SOL = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String TRESC =
        "{\"title\":\"Już jutro\",\"body\":\"Nocny koncert — Kraków\",\"url\":\"/wydarzenia/12\"}";
    static final String WYNIK = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocIn"
        + "mYWAmS6TlzAC8wEqKK6PBru3jl7A_e3c9cFr7_NBuemfu8ZgVV6HH2uvkZSMuK92Fe2_jeRPfRR3goD2DZUxPmjp0vdBsyHeQTQ"
        + "takyOc34NmUOHOu93Cg28dQSa3SZqtsB24K444g60mw9mdspUw_ScYZi_SR";

    @Test
    @DisplayName("te same klucze i sol daja dokladnie te bajty co biblioteka referencyjna")
    void matchesReference() {
        KeyPair nadawca = new KeyPair(
            P256.publicKey(P256.fromBase64(NADAWCA_PUBLICZNY)),
            P256.privateKey(P256.fromBase64(NADAWCA_PRYWATNY)));

        byte[] wynik = WebPushEncryption.encrypt(TRESC.getBytes(StandardCharsets.UTF_8),
            P256.fromBase64(ODBIORCA_PUBLICZNY), P256.fromBase64(AUTH), nadawca, P256.fromBase64(SOL));

        assertThat(P256.base64(wynik)).isEqualTo(WYNIK);
    }

    @Test
    @DisplayName("kazde wyslanie ma nowa sol i nowy klucz nadawcy")
    void freshKeysEachTime() {
        byte[] a = WebPushEncryption.encrypt(new byte[] {1}, P256.fromBase64(ODBIORCA_PUBLICZNY), P256.fromBase64(AUTH));
        byte[] b = WebPushEncryption.encrypt(new byte[] {1}, P256.fromBase64(ODBIORCA_PUBLICZNY), P256.fromBase64(AUTH));

        // sol (16 bajtow) i klucz nadawcy (od bajtu 21) - rozne
        assertThat(java.util.Arrays.copyOf(a, 16)).isNotEqualTo(java.util.Arrays.copyOf(b, 16));
        assertThat(java.util.Arrays.copyOfRange(a, 21, 86)).isNotEqualTo(java.util.Arrays.copyOfRange(b, 21, 86));
        assertThat(a.length).isEqualTo(16 + 4 + 1 + 65 + 1 + 1 + 16);
    }

    @Test
    @DisplayName("klucz przegladarki spoza krzywej jest odrzucany")
    void rejectsPointOffCurve() {
        byte[] zly = P256.fromBase64(ODBIORCA_PUBLICZNY);
        zly[64] ^= 1;

        assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[] {1}, zly, P256.fromBase64(AUTH)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("krzywej");
    }

    @Test
    @DisplayName("za dluga tresc i zly sekret auth - blad, a nie ucieta wiadomosc")
    void rejectsBadInput() {
        byte[] klucz = P256.fromBase64(ODBIORCA_PUBLICZNY);
        assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[WebPushEncryption.MAX_PLAINTEXT + 1], klucz,
            P256.fromBase64(AUTH))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[] {1}, klucz, new byte[8]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
