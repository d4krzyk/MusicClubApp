package com.musicclubapp.gif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.musicclubapp.entity.GifAttachment;
import com.musicclubapp.error.InvalidGifException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Podpisuje wyniki wyszukiwania i sprawdza je przy dodawaniu GIF-a do komentarza albo wiadomosci.
 *
 * <p>Zeby klient nie mogl dolaczyc dowolnego adresu obrazka (sledzacego piksela, cudzej tresci), przegladarka
 * dostaje z wyszukiwania nie tylko adresy, ale i {@code token}: zakodowane dane GIF-a z podpisem HMAC-SHA256.
 * Serwer przyjmuje wylacznie taki token, nie adres - nie musi wiec znac listy serwerow CDN dostawcy.
 * Klucz podpisu wywodzi sie z {@code app.remember-me.key} (na produkcji obowiazkowy i wlasny), wiec nie ma
 * dodatkowego sekretu do ustawienia; jego zmiana unieważnia tokeny z otwartych kart, ale nie zapisane GIF-y.</p>
 */
public class GifSigner {

    private static final Base64.Encoder KODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEKODER = Base64.getUrlDecoder();
    private static final String ALGORYTM = "HmacSHA256";

    private final ObjectMapper json = new ObjectMapper();
    private final byte[] key;

    public GifSigner(String secret) {
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                .digest(("musicclub-gifs|" + secret).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String sign(GifItem item) {
        ObjectNode payload = json.createObjectNode()
            .put("u", item.url())
            .put("p", item.previewUrl())
            .put("w", item.width())
            .put("h", item.height())
            .put("t", item.title() == null ? "" : item.title());
        String body;
        try {
            body = KODER.encodeToString(json.writeValueAsBytes(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return body + "." + mac(body);
    }

    /** Odczytuje dane z tokenu; rzuca {@link InvalidGifException}, gdy podpis sie nie zgadza albo dane sa dziwne. */
    public GifAttachment verify(String token) {
        if (token == null || token.length() > 2000) {
            throw new InvalidGifException("brak tokenu albo za dlugi");
        }
        int kropka = token.indexOf('.');
        if (kropka <= 0 || kropka == token.length() - 1) {
            throw new InvalidGifException("zly ksztalt tokenu");
        }
        String body = token.substring(0, kropka);
        byte[] expected = mac(body).getBytes(StandardCharsets.UTF_8);
        byte[] given = token.substring(kropka + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, given)) {
            throw new InvalidGifException("podpis sie nie zgadza");
        }

        JsonNode node;
        try {
            node = json.readTree(DEKODER.decode(body));
        } catch (Exception e) {
            throw new InvalidGifException("nie da sie odczytac tokenu");
        }
        String url = node.path("u").asText("");
        String preview = node.path("p").asText("");
        if (!GifHttp.safeUrl(url) || !GifHttp.safeUrl(preview)) {
            // Podpisane przez nas, wiec nie powinno sie zdarzyc - ale to ostatnia bariera przed zapisem do bazy
            throw new InvalidGifException("adres poza dozwolonymi schematami");
        }
        return new GifAttachment(url, preview, node.path("w").asInt(0), node.path("h").asInt(0),
            GifHttp.clip(node.path("t").asText(""), GifAttachment.MAX_TITLE));
    }

    private String mac(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORYTM);
            mac.init(new SecretKeySpec(key, ALGORYTM));
            return KODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Krotki, stabilny skrot (hex), z ktorego nie da sie odczytac wejscia - pseudonim dla dostawcy GIF-ow. */
    public String pseudonym(Object input) {
        String mac = mac("pseudonym|" + input);
        return mac.substring(0, Math.min(16, mac.length()));
    }
}
