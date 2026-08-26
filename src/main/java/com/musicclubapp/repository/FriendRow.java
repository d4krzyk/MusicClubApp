package com.musicclubapp.repository;

/**
 * Jeden wiersz listy znajomych: dane osoby + wyliczony wynik powiazania.
 *
 * <p>To <b>projekcja</b> - interfejs, ktory Spring Data wypelnia sam na
 * podstawie <b>nazw kolumn</b> zwroconych przez zapytanie. Zadnej klasy
 * z implementacja nie piszemy.</p>
 *
 * <p>Dlaczego nie zwracamy po prostu encji {@code User}? Bo obok danych
 * uzytkownika potrzebujemy kolumny WYLICZONEJ ({@code wspolniZnajomi}),
 * ktorej w tabeli {@code users} nie ma. Encja nie ma gdzie takiej wartosci
 * przyjac, a projekcja - owszem.</p>
 *
 * <p><b>Uwaga:</b> nazwy metod musza pasowac do aliasow w zapytaniu
 * ({@code AS avatarFileName} itd.). Dlatego w zapytaniu aliasujemy KAZDA
 * kolumne jawnie, zamiast liczyc na to, ze {@code avatar_file_name} samo
 * dopasuje sie do {@code getAvatarFileName()}.</p>
 */
public interface FriendRow {

    String getUsername();

    String getAvatarFileName();

    /**
     * Ilu znajomych ma ta osoba wspolnie z ogladajacym.
     *
     * <p>Po tej liczbie sortujemy liste - to jest owo "najbardziej powiazani".
     * Gdy dojda artysci ze Spotify, doliczymy do wyniku takze wspolnych
     * artystow i gatunki; nazwa metody i cala reszta zostaje bez zmian.</p>
     */
    long getWspolniZnajomi();
}
