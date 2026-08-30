package com.musicclubapp.entity;

/**
 * Rodzaj kary czasowej nakladanej na konto.
 *
 * <p><b>Czego brakowalo.</b> Aplikacja zna dwie kary - zakaz publikowania
 * i zakaz wysylania wiadomosci - ale nie mialo tego <i>nazwy</i>. Skutek byl
 * taki, ze jedna koncepcja stala w kodzie <b>dwa razy</b>, w dziesieciu
 * miejscach naraz: dwa rekordy zapytania, dwie metody serwisu, dwa endpointy,
 * dwie fabryki wyjatkow, dwie metody w encji i dwa komplety obslugi
 * w przegladarce. Po znormalizowaniu nazw obie metody serwisu okazaly sie
 * <b>identyczne co do znaku</b>.</p>
 *
 * <p><b>Dlaczego to bylo grozne, a nie tylko rozwlekle.</b> Kazda zmiana
 * w karach wymagala poprawki w dwoch miejscach - i wystarczylo zapomniec
 * o jednym, zeby kary zaczely sie roznic bez powodu. Wlasnie tak powstal
 * blad, w ktorym zdejmowanie zakazu nie dzialalo: poprawka trafila do jednej
 * sciezki, a druga zostala.</p>
 *
 * <p><b>Czego ten enum NIE zmienia.</b> W bazie nadal sa dwie kolumny
 * ({@code posting_banned_until}, {@code messaging_banned_until}) i to jest
 * celowe - to sa dwie niezalezne kary, kazda z wlasnym terminem konca,
 * i tak ma zostac. Wspolna staje sie <b>obsluga</b>, a nie zapis. Wybor
 * kolumny odbywa sie odtad w jednym miejscu: w {@link User}.</p>
 */
public enum BanKind {

    /** Nie wolno dodawac ani edytowac postow. */
    POSTING("error.ban.posting"),

    /** Nie wolno pisac na czacie. */
    MESSAGING("error.ban.messaging");

    /**
     * Poczatek klucza komunikatu dla ukaranego.
     *
     * <p>Trzymamy go <b>przy rodzaju kary</b>, a nie w miejscu rzucania
     * wyjatku. Dzieki temu dolozenie trzeciej kary sprowadza sie do jednej
     * pozycji na tej liscie plus dwoch napisow w tlumaczeniach - a nie do
     * obchodu po wszystkich plikach, ktore o karach cokolwiek wiedza.</p>
     *
     * <p>Pelny klucz powstaje z tego przedrostka: {@code error.ban.posting}
     * dla kary z terminem i {@code error.ban.posting.forever} dla
     * bezterminowej.</p>
     */
    private final String messageKey;

    BanKind(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Klucz komunikatu zaleznie od tego, czy kara ma koniec. */
    public String messageKey(boolean forever) {
        return forever ? messageKey + ".forever" : messageKey;
    }
}
