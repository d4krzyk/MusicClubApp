package com.musicclubapp.repository;

/**
 * Jeden wiersz wyniku zapytania o proponowanych znajomych.
 *
 * <p>To <b>projekcja interfejsowa</b> Spring Data - nie piszemy implementacji,
 * Spring tworzy ja sam i wypelnia kolumnami o pasujacych nazwach. Uzywamy jej
 * (zamiast encji {@code User}), bo wynik zawiera kolumny, ktorych w zadnej
 * encji nie ma: liczbe wspolnych znajomych, wspolnych artystow i wspolnych
 * gatunkow - wyliczone osobno dla kazdego wiersza.</p>
 *
 * <p><b>Nazwy metod musza pasowac do aliasow w zapytaniu.</b> PostgreSQL
 * sprowadza nieujete w cudzyslowy aliasy do malych liter, ale Spring Data
 * dopasowuje nazwy bez zwracania uwagi na wielkosc liter - dlatego
 * {@code AS sharedArtists} trafia do {@code getSharedArtists()}.</p>
 */
public interface SuggestionRow {

    String getUsername();

    String getAvatarFileName();

    long getSharedFriends();

    long getSharedArtists();

    long getSharedGenres();

    /** Czy ta osoba jest juz naszym znajomym - decyduje o przycisku na karcie. */
    boolean getAlreadyFriend();

    /**
     * Suma punktow dopasowania - ta sama, po ktorej sortuje baza.
     *
     * <p>Oddajemy ja na zewnatrz nie po to, zeby ja pokazywac ("masz 14
     * punktow zgodnosci" nic nikomu nie mowi), tylko zeby frontend wiedzial,
     * czy dopasowanie w ogole istnieje. Przy wyniku 0 karta trafia do sekcji
     * "pozostale osoby", a nie miedzy dopasowanych.</p>
     */
    long getScore();
}
