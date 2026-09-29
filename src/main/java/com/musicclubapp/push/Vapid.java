package com.musicclubapp.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Podpis serwera przy kazdym wyslaniu (VAPID, RFC 8292).
 *
 * Przegladarka zapisuje sie na powiadomienia z naszym kluczem publicznym,
 * a usluga push przyjmuje wiadomosci tylko od tego, kto ma pasujacy klucz
 * prywatny. Obcy serwer, ktory poznal adres subskrypcji, nie wysle na nia
 * niczego.
 */
public final class Vapid {

    /** Tyle wazny jest podpis. RFC pozwala na najwyzej 24 godziny. */
    static final Duration WAZNOSC = Duration.ofHours(12);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ECPrivateKey privateKey;
    private final byte[] publicKey;
    private final String subject;

    /**
     * @param publicKey  65 bajtow, base64url - ten sam, ktory dostaje przegladarka
     * @param privateKey 32 bajty, base64url
     * @param subject    kontakt dla uslugi push: "mailto:..." albo adres https
     */
    public Vapid(String publicKey, String privateKey, String subject) {
        this.publicKey = P256.fromBase64(publicKey);
        ECPublicKey pub = P256.publicKey(this.publicKey);
        this.privateKey = P256.privateKey(P256.fromBase64(privateKey));
        if (subject == null || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
            throw new IllegalArgumentException(
                "VAPID_SUBJECT musi zaczynac sie od mailto: albo https:// (jest: \"" + subject + "\")");
        }
        this.subject = subject;
        sprawdzPare(pub);
    }

    /** Klucz, ktory przegladarka podaje przy zapisie (applicationServerKey). */
    public String publicKey() {
        return P256.base64(publicKey);
    }

    /** Naglowek Authorization dla jednego wyslania na dany adres. */
    public String authorization(URI endpoint, Instant now) {
        Map<String, Object> naglowek = new LinkedHashMap<>();
        naglowek.put("typ", "JWT");
        naglowek.put("alg", "ES256");
        Map<String, Object> tresc = new LinkedHashMap<>();
        // Odbiorca to sama usluga push: schemat, host i ewentualnie port
        tresc.put("aud", endpoint.getScheme() + "://" + endpoint.getRawAuthority());
        tresc.put("exp", now.plus(WAZNOSC).getEpochSecond());
        tresc.put("sub", subject);

        String doPodpisu = b64(json(naglowek)) + "." + b64(json(tresc));
        return "vapid t=" + doPodpisu + "." + P256.base64(podpisz(doPodpisu.getBytes(StandardCharsets.US_ASCII)))
            + ", k=" + publicKey();
    }

    /**
     * Czy klucz prywatny pasuje do publicznego. Pomylka (np. klucze z dwoch
     * roznych generowan) nie dalaby zadnego bledu u nas - tylko kazda
     * wiadomosc bylaby po cichu odrzucana przez usluge push.
     */
    private void sprawdzPare(ECPublicKey pub) {
        try {
            byte[] proba = "musicclub".getBytes(StandardCharsets.US_ASCII);
            Signature weryfikacja = Signature.getInstance("SHA256withECDSAinP1363Format");
            weryfikacja.initVerify(pub);
            weryfikacja.update(proba);
            if (!weryfikacja.verify(podpisz(proba))) {
                throw new IllegalArgumentException(
                    "VAPID_PRIVATE_KEY nie pasuje do VAPID_PUBLIC_KEY - oba musza pochodzic z jednego generowania");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ES256 w postaci z JWT: r i s po 32 bajty, bez opakowania DER. */
    private byte[] podpisz(byte[] dane) {
        try {
            Signature podpis = Signature.getInstance("SHA256withECDSAinP1363Format");
            podpis.initSign(privateKey);
            podpis.update(dane);
            return podpis.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] json(Map<String, Object> mapa) {
        try {
            return JSON.writeValueAsBytes(mapa);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return P256.base64(bytes);
    }
}
