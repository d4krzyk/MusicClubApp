package com.musicclubapp.service;

import java.util.List;
import java.util.Locale;

/**
 * Kraje, z ktorych mozna ogladac wydarzenia.
 *
 * Tylko te, w ktorych Ticketmaster sprzedaje bilety pod wlasna marka - poza
 * nimi Discovery API nie ma czego oddac. Polske sprawdzilismy wprost (794
 * koncerty); dla pozostalych liczba wydarzen wyjdzie po pierwszym imporcie.
 */
public final class EventCountries {

    public static final String DOMYSLNY = "PL";

    public static final List<String> OBSLUGIWANE = List.of(
        "PL", "CZ", "SK", "DE", "AT", "CH", "NL", "BE", "DK", "SE", "NO", "FI",
        "GB", "IE", "ES", "IT", "FR", "US", "CA");

    private EventCountries() {
    }

    public static boolean supported(String code) {
        return code != null && OBSLUGIWANE.contains(code.toUpperCase(Locale.ROOT));
    }

    /** Kraj konta albo Polska, gdy nic nie wybrano albo wybrano cos, czego juz nie obslugujemy. */
    public static String orDefault(String code) {
        return supported(code) ? code.toUpperCase(Locale.ROOT) : DOMYSLNY;
    }
}
