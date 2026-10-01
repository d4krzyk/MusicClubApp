package com.musicclubapp.service;

/**
 * Jak bardzo "blisko" jest druga osoba, klan albo koncert - w jednej skali dla wszystkich miejsc, w
 * ktorych lokalizacja wplywa na kolejnosc: propozycje znajomych, wydarzenia, klany i tablica.
 *
 * <p>Poziom to liczba od 0 do 5; kazde miejsce mnozy go przez wlasna wage (propozycje licza punkty
 * z artystow po 5, koncerty z artystow po 100). Poziom 5 to to samo miasto, 0 - dalej niz 250 km
 * albo nie wiadomo gdzie.</p>
 *
 * <p>Progi sa tez w zapytaniu SQL o propozycje znajomych ({@link #SQL_LEVEL}) - to jedyne miejsce, ktore
 * musi policzyc to w bazie, bo od tego zalezy, kto zmiesci sie na liscie. Test sprawdza, ze baza i Java
 * dochodza do tych samych liczb.</p>
 */
public final class LocationScore {

    /** To samo miasto (albo mniej niz kilometr). */
    public static final int SAME_CITY_KM = 1;
    /** Aglomeracja: Poznan - Swarzedz, Gdansk - Gdynia. */
    public static final int NEAR_KM = 30;
    public static final int AREA_KM = 60;
    public static final int REGION_KM = 120;
    public static final int FAR_KM = 250;

    public static final int MAX_LEVEL = 5;

    private LocationScore() {
    }

    /** Poziom bliskosci 0-5 dla odleglosci w km; {@code null} (nie wiadomo) to 0. */
    public static int level(Double km) {
        if (km == null) {
            return 0;
        }
        if (km < SAME_CITY_KM) {
            return 5;
        }
        if (km <= NEAR_KM) {
            return 4;
        }
        if (km <= AREA_KM) {
            return 3;
        }
        if (km <= REGION_KM) {
            return 2;
        }
        if (km <= FAR_KM) {
            return 1;
        }
        return 0;
    }

    /**
     * To samo co {@link #level} w SQL, dla kolumny {@code distance_km} (NULL = nie wiadomo). Zlozone
     * ze stalych, zeby dalo sie wstawic do adnotacji {@code @Query}.
     */
    public static final String SQL_LEVEL = "CASE WHEN distance_km IS NULL THEN 0"
        + " WHEN distance_km < " + SAME_CITY_KM + " THEN 5"
        + " WHEN distance_km <= " + NEAR_KM + " THEN 4"
        + " WHEN distance_km <= " + AREA_KM + " THEN 3"
        + " WHEN distance_km <= " + REGION_KM + " THEN 2"
        + " WHEN distance_km <= " + FAR_KM + " THEN 1"
        + " ELSE 0 END";

    /**
     * Odleglosc w km miedzy miastem ogladajacego ({@code ja}) a miastem uzytkownika {@code u} w
     * zapytaniu o propozycje znajomych. {@code LEAST/GREATEST} chroni {@code ACOS} przed wynikiem
     * 1.0000000000000002 z bledu zaokraglen (to byl blad "domena" zamiast zera).
     */
    public static final String SQL_DISTANCE_JA_U = "6371 * ACOS(LEAST(1, GREATEST(-1,"
        + " COS(RADIANS(ja.city_lat)) * COS(RADIANS(u.city_lat)) * COS(RADIANS(u.city_lon) - RADIANS(ja.city_lon))"
        + " + SIN(RADIANS(ja.city_lat)) * SIN(RADIANS(u.city_lat)))))";

    /** Grubo: czy to to samo miasto, aglomeracja albo dalej - do podpisow, ktore nie zdradzaja odleglosci. */
    public enum Closeness {
        SAME_CITY, NEARBY;

        /** {@code null}, gdy dalej niz aglomeracja albo nie wiadomo. */
        public static Closeness of(Double km) {
            if (km == null) {
                return null;
            }
            if (km < SAME_CITY_KM) {
                return SAME_CITY;
            }
            return km <= AREA_KM ? NEARBY : null;
        }
    }
}
