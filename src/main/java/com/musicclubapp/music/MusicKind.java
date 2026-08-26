package com.musicclubapp.music;

/**
 * Co dokladnie wrzucamy: pojedynczy utwor, caly album czy profil artysty.
 *
 * <p><b>To uzytkownik wybiera rodzaj</b> przelacznikiem w formularzu, a my
 * sprawdzamy, czy wklejony link mu odpowiada. Moglibysmy rozpoznawac rodzaj
 * z samego adresu, ale wtedy pomylka konczy sie cicha niespodzianka
 * ("wrzucalem album, a wyszedl utwor"). Przy jawnym wyborze niezgodnosc
 * to blad, ktory widac od razu.</p>
 */
public enum MusicKind {

    /** Pojedynczy utwor - JEDYNY rodzaj, przy ktorym da sie wskazac moment startu. */
    TRACK,

    ALBUM,

    ARTIST;

    /**
     * Czy przy tym rodzaju ma sens wybieranie momentu, od ktorego zaczyna
     * sie odtwarzanie.
     *
     * <p>Album to wiele utworow, a profil artysty to w ogole nie jest
     * nagranie - w obu przypadkach "zacznij od 1:30" nic nie znaczy.
     * Dlatego formularz chowa wtedy to pole, a serwer odrzuca probe
     * przeslania takiej wartosci.</p>
     */
    public boolean obslugujeMomentStartu() {
        return this == TRACK;
    }
}
