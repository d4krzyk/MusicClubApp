package com.musicclubapp.service;

/**
 * Jak bardzo gust drugiej osoby pasuje do mojego - w trybie Poznawaj. Punkty: wspolny ulubiony wykonawca 5,
 * wspolny ulubiony utwor 3, wspolny gatunek 1 (te same wagi co kolejnosc talii w zapytaniu). Poziom 0-4 to
 * podpis na karcie i kreski miernika - zamiast procentow, ktore udawalyby precyzje, ktorej tu nie ma.
 *
 * <p>Przyklady: jeden wspolny wykonawca i dwa gatunki = 7 pkt = poziom 2 ("podobny gust"); trzech wykonawcow,
 * utwor i piec gatunkow = 23 pkt = poziom 3; dwa gatunki = poziom 1.</p>
 */
public final class DiscoverMatch {

    public static final int ARTIST = 5;
    public static final int TRACK = 3;
    public static final int GENRE = 1;

    /** Progi poziomow 1, 2, 3, 4. */
    static final int[] PROGI = {1, 5, 12, 25};

    private DiscoverMatch() {
    }

    public static long score(long artists, long tracks, long genres) {
        return ARTIST * artists + TRACK * tracks + GENRE * genres;
    }

    /** 0 = nic wspolnego (cos nowego), 4 = muzyczna bratnia dusza. */
    public static int level(long score) {
        int poziom = 0;
        for (int prog : PROGI) {
            if (score >= prog) {
                poziom++;
            }
        }
        return poziom;
    }
}
