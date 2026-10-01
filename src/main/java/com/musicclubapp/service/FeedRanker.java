package com.musicclubapp.service;

import com.musicclubapp.dto.FeedReason;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ocena posta osoby spoza kregu na tablicy "Dla ciebie".
 *
 * <p>{@code ocena = swiezosc × (podstawa + okolica + gust + popularnosc)}. Swiezosc maleje o polowe co 72
 * godziny i MNOZY reszte, wiec kolejnosc dwoch postow zalezy tylko od roznicy ich wieku - nie zmienia sie
 * miedzy pobraniem pierwszej a drugiej strony. Reszta to cztery skladniki od 0 do 1:</p>
 *
 * <ul>
 *   <li>podstawa 0,4 - post bez niczego szczegolnego, tylko swiezy;</li>
 *   <li>okolica (poziom bliskosci 0-5, {@link LocationScore}) razy 1,0;</li>
 *   <li>gust: wspolni ulubieni wykonawcy (3 pkt) i gatunki (1 pkt), 10 pkt = maksimum, razy 0,7;</li>
 *   <li>popularnosc: reakcje + 2 × komentarze, tlumiona logarytmem (20 = maksimum), razy 0,6.</li>
 * </ul>
 *
 * <p>Przyklad: swiezy post z tego samego miasta ma {@code 1 × (0,4 + 1,0) = 1,4}; trzydniowy z miasta, gustu
 * i duzym zainteresowaniem - {@code 0,5 × (0,4 + 1,0 + 0,7 + 0,6) = 1,35}; sprzed miesiaca nawet z kompletem
 * punktow zostaje daleko w tyle. Zwykly swiezy post bez niczego ({@code 0,4}) przegrywa ze starszym, ale
 * "wartym uwagi".</p>
 */
public final class FeedRanker {

    public static final double POLOWA_ZYCIA_GODZIN = 72;
    static final double PODSTAWA = 0.4;
    static final double WAGA_OKOLICY = 1.0;
    static final double WAGA_GUSTU = 0.7;
    static final double WAGA_POPULARNOSCI = 0.6;

    /** Od takiego "zainteresowania" (reakcje + 2 × komentarze) post dostaje plakietke "popularne". */
    static final int POPULARNE_OD = 5;
    private static final double POPULARNOSC_MAKS = 20;

    private FeedRanker() {
    }

    /** Wszystko, co o poscie wiemy do jego oceny. */
    public record Signals(LocalDateTime createdAt, int nearLevel, long sharedArtists, long sharedGenres,
                          long reactions, long comments) {

        long engagement() {
            return reactions + 2 * comments;
        }
    }

    public static double score(Signals s, LocalDateTime now) {
        double godziny = Math.max(0, Duration.between(s.createdAt(), now).toMinutes() / 60.0);
        double swiezosc = Math.pow(0.5, godziny / POLOWA_ZYCIA_GODZIN);
        double okolica = s.nearLevel() / (double) LocationScore.MAX_LEVEL;
        double gust = Math.min(1.0, (3.0 * s.sharedArtists() + s.sharedGenres()) / 10.0);
        double popularnosc = Math.min(1.0, Math.log1p(s.engagement()) / Math.log1p(POPULARNOSC_MAKS));
        return swiezosc * (PODSTAWA + WAGA_OKOLICY * okolica + WAGA_GUSTU * gust + WAGA_POPULARNOSCI * popularnosc);
    }

    /**
     * Powody, ktore pokazujemy przy poscie. Okolice tylko wtedy, gdy autor pokazuje swoje miasto - sama
     * kolejnosc tablicy uzywa jej zawsze, ale podpis "z twojej okolicy" jest informacja o tej osobie.
     */
    public static List<FeedReason> reasons(Signals s, boolean authorShowsCity) {
        List<FeedReason> powody = new ArrayList<>();
        if (authorShowsCity && s.nearLevel() >= 3) {
            powody.add(FeedReason.NEAR);
        }
        if (s.sharedArtists() >= 1 || s.sharedGenres() >= 2) {
            powody.add(FeedReason.TASTE);
        }
        if (s.engagement() >= POPULARNE_OD) {
            powody.add(FeedReason.POPULAR);
        }
        return powody;
    }
}
