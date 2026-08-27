package com.musicclubapp.entity;

/**
 * Kto moze zobaczyc post.
 *
 * <p><b>Dwie wartosci, a nie piec.</b> Kusi, zeby dolozyc "tylko ja"
 * i "znajomi znajomych", ale kazda kolejna mozliwosc to kolejna regula,
 * ktorej trzeba pilnowac w <i>kazdym</i> zapytaniu o posty - a uzytkownik
 * i tak musi za kazdym razem zdecydowac, ktora wybrac. Dwie mozliwosci
 * odpowiadaja na jedyne pytanie, ktore naprawde sie tu zadaje: czy to jest
 * dla wszystkich, czy dla swoich.</p>
 *
 * <p><b>Wartosci trafiaja do bazy jako tekst</b> ({@code EnumType.STRING}),
 * wiec dopisanie trzeciej w przyszlosci nie przestawi znaczenia juz
 * zapisanych wierszy. Trzeba wtedy pamietac o {@code EnumConstraintRefresher} -
 * ograniczenie CHECK w istniejacej bazie samo sie nie poszerzy.</p>
 */
public enum PostVisibility {

    /** Widza wszyscy zalogowani - takze osoby spoza znajomych. */
    PUBLIC,

    /**
     * Widzi autor i jego znajomi.
     *
     * <p><b>Administrator tez nie widzi takiego posta na tablicy</b> i nie jest
     * to przeoczenie. Interfejs obiecuje uzytkownikowi "tylko znajomi";
     * obietnica z cichym wyjatkiem dla obslugi serwisu nie jest obietnica.
     * Moderacja przez usuwanie po identyfikatorze dziala dalej - admin moze
     * skasowac post, ktorego nie czyta, i to wystarcza.</p>
     */
    FRIENDS
}
