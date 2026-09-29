package com.musicclubapp.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Drobiazgi wokol adresow e-mail: zapis, ukrywanie w komunikatach, skrzynki jednorazowe. */
public final class EmailAddresses {

    /**
     * Domeny skrzynek "na dziesiec minut". Potwierdzenie adresu nic nie daje,
     * gdy adres zyje krocej niz konto - a wlasnie takie zaklada sie do trollowania.
     */
    private static final Set<String> JEDNORAZOWE = wczytaj("mail/disposable-domains.txt");

    private EmailAddresses() {
    }

    /**
     * Adres w postaci, w jakiej go zapisujemy: bez spacji i malymi literami.
     * Wielkosc liter w adresie w praktyce nie ma znaczenia - bez tego
     * "Jan@x.pl" i "jan@x.pl" bylyby dwoma kontami na te sama skrzynke.
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Czy adres jest ze skrzynki jednorazowej - takze z jej poddomeny. */
    public static boolean isDisposable(String email) {
        if (email == null) {
            return false;
        }
        int malpa = email.lastIndexOf('@');
        if (malpa < 0) {
            return false;
        }
        String domena = normalize(email.substring(malpa + 1));
        while (true) {
            if (JEDNORAZOWE.contains(domena)) {
                return true;
            }
            int kropka = domena.indexOf('.');
            if (kropka < 0) {
                return false;
            }
            domena = domena.substring(kropka + 1);
        }
    }

    /**
     * "jan.kowalski@gmail.com" -&gt; "j***i@gmail.com". Tyle wystarczy wlascicielowi,
     * zeby poznac swoj adres, a nie zdradza go komus, kto zna tylko login.
     */
    public static String mask(String email) {
        if (email == null) {
            return "";
        }
        int malpa = email.lastIndexOf('@');
        if (malpa <= 0) {
            return "***";
        }
        String nazwa = email.substring(0, malpa);
        String poczatek = nazwa.substring(0, 1);
        String koniec = nazwa.length() > 2 ? nazwa.substring(nazwa.length() - 1) : "";
        return poczatek + "***" + koniec + email.substring(malpa);
    }

    private static Set<String> wczytaj(String zasob) {
        try (InputStream in = new ClassPathResource(zasob).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .map(l -> l.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Brak listy domen jednorazowych: " + zasob, e);
        }
    }
}
