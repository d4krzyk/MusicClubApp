package com.musicclubapp.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Klucze na krzywej P-256 w postaci, w jakiej chodza po sieci przy Web Push:
 * klucz publiczny jako 65 bajtow (0x04, x, y), prywatny jako 32 bajty,
 * oba zapisane base64url bez "=" na koncu.
 */
public final class P256 {

    public static final ECParameterSpec PARAMS = parametry();

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private P256() {
    }

    private static ECParameterSpec parametry() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Brak krzywej P-256 w JDK", e);
        }
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(PARAMS);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Klucz publiczny z 65 bajtow. Punkt musi lezec na krzywej - klucz
     * przychodzi z przegladarki, a podstawiony punkt spoza krzywej to znany
     * sposob na wyciaganie klucza prywatnego z ECDH.
     */
    public static ECPublicKey publicKey(byte[] raw) {
        if (raw == null || raw.length != 65 || raw[0] != 0x04) {
            throw new IllegalArgumentException("Klucz publiczny P-256 ma 65 bajtow i zaczyna sie od 0x04");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        if (!naKrzywej(x, y)) {
            throw new IllegalArgumentException("Punkt nie lezy na krzywej P-256");
        }
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), PARAMS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Niepoprawny klucz publiczny", e);
        }
    }

    public static ECPrivateKey privateKey(byte[] raw) {
        if (raw == null || raw.length != 32) {
            throw new IllegalArgumentException("Klucz prywatny P-256 ma 32 bajty");
        }
        BigInteger d = new BigInteger(1, raw);
        if (d.signum() == 0 || d.compareTo(PARAMS.getOrder()) >= 0) {
            throw new IllegalArgumentException("Klucz prywatny spoza zakresu krzywej");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(d, PARAMS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Niepoprawny klucz prywatny", e);
        }
    }

    /** 65 bajtow: 0x04, x i y, kazda po 32 bajty. */
    public static byte[] encode(ECPublicKey key) {
        byte[] wynik = new byte[65];
        wynik[0] = 0x04;
        wpisz(key.getW().getAffineX(), wynik, 1);
        wpisz(key.getW().getAffineY(), wynik, 33);
        return wynik;
    }

    /** 32 bajty skalara. */
    public static byte[] encode(ECPrivateKey key) {
        byte[] wynik = new byte[32];
        wpisz(key.getS(), wynik, 0);
        return wynik;
    }

    public static String base64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    /** base64url z "=" albo bez - przegladarki i generatory kluczy roznie to podaja. */
    public static byte[] fromBase64(String text) {
        String bez = text.trim().replace("=", "").replace('+', '-').replace('/', '_');
        return B64D.decode(bez);
    }

    private static void wpisz(BigInteger liczba, byte[] cel, int od) {
        byte[] b = liczba.toByteArray();
        // toByteArray dodaje zero z przodu przy najstarszym bicie ustawionym albo daje mniej bajtow
        int start = Math.max(0, b.length - 32);
        int dlugosc = b.length - start;
        System.arraycopy(b, start, cel, od + 32 - dlugosc, dlugosc);
    }

    private static boolean naKrzywej(BigInteger x, BigInteger y) {
        BigInteger p = ((ECFieldFp) PARAMS.getCurve().getField()).getP();
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger lewa = y.modPow(BigInteger.TWO, p);
        BigInteger prawa = x.pow(3)
            .add(PARAMS.getCurve().getA().multiply(x))
            .add(PARAMS.getCurve().getB())
            .mod(p);
        return lewa.equals(prawa);
    }
}
