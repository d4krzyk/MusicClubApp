package com.musicclubapp.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * Szyfrowanie tresci powiadomienia push (RFC 8291, kodowanie aes128gcm z RFC 8188).
 *
 * Usluga push (Google, Mozilla, Apple) przenosi wiadomosc, ale nie moze jej
 * przeczytac: szyfrujemy kluczem, ktory przegladarka podala przy zapisie
 * (p256dh), razem z jej sekretem (auth). Kazda wiadomosc ma nowa, jednorazowa
 * pare kluczy i nowa sol.
 */
public final class WebPushEncryption {

    /** Rozmiar rekordu. Wiadomosc miesci sie w jednym, wiec to tylko liczba w naglowku. */
    static final int RECORD_SIZE = 4096;

    /** Tyle tresci sie zmiesci: rekord minus znacznik GCM (16) i bajt konca (1). */
    public static final int MAX_PLAINTEXT = RECORD_SIZE - 16 - 1;

    private static final SecureRandom LOSOWE = new SecureRandom();

    private WebPushEncryption() {
    }

    public static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        LOSOWE.nextBytes(salt);
        return encrypt(plaintext, userAgentPublic, authSecret, P256.generate(), salt);
    }

    /** Z podana para nadawcy i sola - do sprawdzenia na wzorcu. */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret,
                          KeyPair sender, byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("Tresc powiadomienia za dluga: " + plaintext.length);
        }
        if (authSecret == null || authSecret.length != 16) {
            throw new IllegalArgumentException("Sekret auth ma 16 bajtow");
        }
        try {
            byte[] senderPublic = P256.encode((ECPublicKey) sender.getPublic());

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(sender.getPrivate());
            ecdh.doPhase(P256.publicKey(userAgentPublic), true);
            byte[] wspolny = ecdh.generateSecret();

            // RFC 8291, 3.4: klucz wejsciowy z sekretu ECDH i sekretu auth
            byte[] prkKey = hmac(authSecret, wspolny);
            byte[] keyInfo = sklej(ascii("WebPush: info\0"), userAgentPublic, senderPublic);
            byte[] ikm = hmac(prkKey, sklej(keyInfo, new byte[] {1}));

            // RFC 8188, 2.2: klucz tresci i nonce z soli
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, sklej(ascii("Content-Encoding: aes128gcm\0"), new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, sklej(ascii("Content-Encoding: nonce\0"), new byte[] {1})), 12);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // Jedyny (a wiec ostatni) rekord konczy sie bajtem 0x02
            byte[] szyfr = aes.doFinal(sklej(plaintext, new byte[] {2}));

            ByteBuffer naglowek = ByteBuffer.allocate(16 + 4 + 1 + senderPublic.length);
            naglowek.put(salt).putInt(RECORD_SIZE).put((byte) senderPublic.length).put(senderPublic);
            return sklej(naglowek.array(), szyfr);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nie udalo sie zaszyfrowac powiadomienia", e);
        }
    }

    private static byte[] hmac(byte[] klucz, byte[] dane) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(klucz, "HmacSHA256"));
        return mac.doFinal(dane);
    }

    private static byte[] ascii(String tekst) {
        return tekst.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] sklej(byte[]... czesci) {
        ByteArrayOutputStream wynik = new ByteArrayOutputStream();
        for (byte[] c : czesci) {
            wynik.writeBytes(c);
        }
        return wynik.toByteArray();
    }
}
