package com.musicclubapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formulki sprzedazowe i prawne z opisow Ticketmastera - to nie jest opis koncertu.
 *
 * Na zywym Ticketmasterze pole "info" polskich wydarzen to zwykle sam regulamin sprzedazy: oplata serwisowa, "plan sali
 * ma charakter pogladowy", bilety dla osob z niepelnosprawnosciami "prosimy o kontakt" (z Ticketmasterem, nie z nami)
 * i dane spolki organizatora z KRS i VAT. Pod "O wydarzeniu" wygladalo to jak pismo kopiuj-wklej. Wycinamy takie zdania;
 * gdy nic nie zostanie, opisu nie ma (strona nie pokazuje pustej sekcji, a braki moze uzupelnic inne zrodlo).
 *
 * Zdanie konczy kropka, wykrzyknik albo pytajnik przed wielka litera - ale nie po skrocie ("Sp. z o.o.", "ul.", "No.:")
 * i nie po pojedynczej literze, bo dane spolki rozpadlyby sie na kawalki bez znaku rozpoznawczego.
 */
final class TicketBoilerplate {

    /**
     * Rozpoznawane formulki - po angielsku i po polsku (Ticketmaster podaje teksty w obu jezykach). Koncowki slow przez
     * {@code \p{L}}, nie {@code \w} - {@code \w} nie lapie polskich liter ("podlegają").
     */
    private static final Pattern FORMULKA = Pattern.compile(String.join("|",
        // oplaty i ceny
        "service fee", "booking fee", "handling fee", "transaction fee", "additional fees?",
        "conditions agreed with the organi[sz]er", "prices? (include|includes|including) vat",
        "opłat\\p{L}* (serwisow|manipulacyjn|transakcyjn|dodatkow)", "do ceny biletu", "cen\\p{L}* biletu (zawiera|obejmuje)",
        // sala, bilety, sprzedaz
        "seating (chart|plan|map)", "general layout of the venue", "(buy|purchase|book) accessible tickets?",
        "plan (sali|miejsc|obiektu|widowni)", "charakter poglądowy",
        "bilet\\p{L}* dla os(ób|oby) (z niepełnosprawn|niepełnosprawn)",
        "ticket limit", "(limit|maximum) (of )?\\d+ tickets?", "limit biletów", "maks(ymalnie|\\.) \\d+ bilet",
        "non-?refundable", "no refunds?", "nie podlegaj\\p{L}* zwrotowi",
        "ticketmaster app", "mobile tickets?", "print[- ]at[- ]home", "aplikacji ticketmaster", "bilet\\p{L}* mobiln",
        // dane spolki organizatora
        "registered office", "commercial register", "court register", "vat (no|number|id)\\b", "share capital",
        "\\b(krs|nip|regon)\\b\\W{0,3}\\d", "z siedzibą", "kapitał\\p{L}* zakładow", "rejestr\\p{L}* (sądow|przedsiębiorców)"),
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Kandydat na koniec zdania: znak konca, odstep, (cudzyslow albo nawias) i wielka litera. */
    private static final Pattern KONIEC = Pattern.compile("[.!?…]\\s+(?=[\"„«(]?\\p{Lu})");

    /** Skroty, po ktorych kropka nie konczy zdania (porownywane malymi literami). */
    private static final Set<String> SKROTY = Set.of("ul", "al", "pl", "os", "nr", "sp", "św", "ks", "dr", "prof", "inż",
        "mgr", "godz", "ok", "tel", "np", "tj", "tzw", "itd", "itp", "zł", "no", "st", "mr", "mrs", "ms", "jr", "sr", "vs",
        "etc", "ca", "approx", "ltd", "inc", "co", "nip", "krs");

    private TicketBoilerplate() {
    }

    /** Tekst bez formulek, z zachowanymi akapitami; null, gdy nic nie zostalo. */
    static String strip(String tekst) {
        String czysty = PlainText.of(tekst);
        if (czysty == null) {
            return null;
        }
        StringBuilder wynik = new StringBuilder();
        for (String linia : czysty.split("\n", -1)) {
            List<String> zostaja = new ArrayList<>();
            for (String zdanie : zdania(linia)) {
                if (!FORMULKA.matcher(zdanie).find()) {
                    zostaja.add(zdanie);
                }
            }
            wynik.append(String.join(" ", zostaja)).append('\n');
        }
        return PlainText.of(wynik.toString());
    }

    /** Zdania jednej linii - bez dzielenia po skrotach i pojedynczych literach. */
    static List<String> zdania(String linia) {
        List<String> wynik = new ArrayList<>();
        int poczatek = 0;
        Matcher m = KONIEC.matcher(linia);
        while (m.find()) {
            if (skrotPrzed(linia, m.start())) {
                continue;
            }
            wynik.add(linia.substring(poczatek, m.start() + 1).strip());
            poczatek = m.end();
        }
        String reszta = linia.substring(poczatek).strip();
        if (!reszta.isEmpty()) {
            wynik.add(reszta);
        }
        return wynik;
    }

    /** Czy przed znakiem na pozycji {@code i} stoi skrot albo pojedyncza litera ("Sp", "ul", "o" z "o.o."). */
    private static boolean skrotPrzed(String linia, int i) {
        if (linia.charAt(i) != '.') {
            return false;
        }
        int j = i;
        while (j > 0 && Character.isLetter(linia.charAt(j - 1))) {
            j--;
        }
        String slowo = linia.substring(j, i).toLowerCase(Locale.ROOT);
        return slowo.length() == 1 || SKROTY.contains(slowo);
    }
}
