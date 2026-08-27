package com.musicclubapp.error;

/**
 * Operacja jest technicznie mozliwa, ale zabroniona przez regule biznesowa.
 *
 * <p>Przyklad: administrator probuje odebrac uprawnienia samemu sobie.
 * Zapytanie jest poprawne, uzytkownik ma uprawnienia - po prostu nie wolno
 * tego zrobic.</p>
 *
 * <p>Obslugiwane jako HTTP 409 CONFLICT - wyklad 4 (slajd 32) opisuje ten kod
 * wlasnie jako "probe wykonania niedozwolonej operacji".</p>
 */
public class OperationNotAllowedException extends RuntimeException {

    private final String messageKey;
    private final Object[] arguments;

    private OperationNotAllowedException(String message, String messageKey, Object... arguments) {
        super(message);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    /**
     * Administrator nie moze zmienic wlasnej roli.
     *
     * <p>Bez tej blokady jedno klikniecie potrafiloby zamknac ostatniemu
     * adminowi droge do panelu - i nie byloby juz nikogo, kto moglby mu
     * uprawnienia przywrocic inaczej niz recznie w bazie.</p>
     */
    public static OperationNotAllowedException ownRole() {
        return new OperationNotAllowedException(
            "Administrator nie moze zmienic wlasnej roli", "error.role.self");
    }

    /** Proba usuniecia posta, ktorego sie nie jest autorem (i nie jest sie adminem). */
    public static OperationNotAllowedException someoneElsesPost() {
        return new OperationNotAllowedException(
            "Mozna usuwac tylko wlasne posty", "error.post.notowner");
    }

    /**
     * Proba edycji cudzego posta.
     *
     * <p>Osobny komunikat od usuwania, bo tu nawet administrator nie ma prawa -
     * moderacja polega na kasowaniu, nie na przerabianiu cudzych tresci.</p>
     */
    public static OperationNotAllowedException someoneElsesPostEdit() {
        return new OperationNotAllowedException(
            "Mozna edytowac tylko wlasne posty", "error.post.notauthor");
    }

    /**
     * Proba siegniecia po post przeznaczony tylko dla znajomych autora.
     *
     * <p>Dotyczy tez administratora - patrz {@code PostVisibility.FRIENDS}.
     * Obietnica "widza to tylko znajomi" z cichym wyjatkiem dla obslugi
     * serwisu nie jest obietnica.</p>
     */
    public static OperationNotAllowedException friendsOnlyPost() {
        return new OperationNotAllowedException(
            "Ten post jest widoczny tylko dla znajomych autora", "error.post.friendsonly");
    }

    /** Proba zaproszenia samego siebie do znajomych. */
    public static OperationNotAllowedException invitationToSelf() {
        return new OperationNotAllowedException(
            "Nie mozna zaprosic samego siebie", "error.friend.self");
    }

    /** Te osoby juz sa znajomymi - drugie zaproszenie nie ma sensu. */
    public static OperationNotAllowedException alreadyFriends() {
        return new OperationNotAllowedException(
            "Ta osoba jest juz w znajomych", "error.friend.already");
    }

    /** Zaproszenie do tej osoby juz czeka na odpowiedz. */
    public static OperationNotAllowedException invitationAlreadySent() {
        return new OperationNotAllowedException(
            "Zaproszenie zostalo juz wyslane", "error.friend.pending");
    }

    /**
     * Proba przyjecia albo odrzucenia cudzego zaproszenia.
     *
     * <p>Bez tego sprawdzenia wystarczyloby zgadnac identyfikator zaproszenia,
     * zeby zaakceptowac znajomosc miedzy dwiema obcymi osobami.</p>
     */
    public static OperationNotAllowedException someoneElsesInvitation() {
        return new OperationNotAllowedException(
            "To nie jest Twoje zaproszenie", "error.friend.notyours");
    }

    /**
     * Osiagnieto gorny limit ulubionych.
     *
     * <p>Limit nie jest zlosliwoscia wobec uzytkownika, tylko warunkiem tego,
     * zeby dopasowywanie ludzi mialo sens. Ktos z lista pieciuset wykonawcow
     * pasowalby do wszystkich naraz - a wtedy nie pasowalby juz do nikogo.</p>
     */
    public static OperationNotAllowedException favoritesLimit() {
        return new OperationNotAllowedException(
            "Osiagnieto limit ulubionych", "error.favorite.limit");
    }

    /**
     * Pozycji nie ma w katalogu Deezera.
     *
     * <p>To wlasnie ta blokada nie pozwala dodac wymyslonego artysty.
     * Nazwa i zdjecie z zapytania sa ignorowane - liczy sie wylacznie to,
     * czy Deezer zna podany identyfikator.</p>
     */
    public static OperationNotAllowedException notInCatalog() {
        return new OperationNotAllowedException(
            "Takiej pozycji nie ma w katalogu", "error.favorite.notincatalog");
    }

    /** Gablotka playlist jest pelna - piec pozycji to jej caly sens. */
    public static OperationNotAllowedException playlistLimit() {
        return new OperationNotAllowedException(
            "Gablotka playlist jest pelna", "error.playlist.limit");
    }

    /**
     * Wklejony adres prowadzi gdzie indziej niz do playlisty.
     *
     * <p>Mowimy wprost, co jest nie tak, bo to najczestsza pomylka:
     * link do pojedynczego utworu wyglada bardzo podobnie.</p>
     */
    public static OperationNotAllowedException notAPlaylist() {
        return new OperationNotAllowedException(
            "To nie jest link do playlisty", "error.playlist.notplaylist");
    }

    /** Ta playlista juz jest w gablotce. */
    public static OperationNotAllowedException playlistAlreadyThere() {
        return new OperationNotAllowedException(
            "Ta playlista juz jest w gablotce", "error.playlist.duplicate");
    }

    /** Proba importu z Last.fm przy niewpisanym kluczu API. */
    public static OperationNotAllowedException lastFmDisabled() {
        return new OperationNotAllowedException(
            "Import z Last.fm jest wylaczony", "error.lastfm.disabled");
    }

    /** Last.fm nie zna takiego uzytkownika - najczesciej zwykla literowka. */
    public static OperationNotAllowedException lastFmUnknownUser() {
        return new OperationNotAllowedException(
            "Last.fm nie zna takiego uzytkownika", "error.lastfm.nouser");
    }

    /** Last.fm nie odpowiada - to awaria po ich stronie, nie blad uzytkownika. */
    public static OperationNotAllowedException lastFmUnavailable() {
        return new OperationNotAllowedException(
            "Last.fm nie odpowiada", "error.lastfm.unavailable");
    }

    /**
     * Administrator nie moze skasowac wlasnego konta z panelu.
     *
     * <p>Ta sama mysl co przy {@link #ownRole()}: jedno klikniecie nie moze
     * zostawic portalu bez nikogo, kto ma do niego dostep. Zwykly uzytkownik,
     * ktory chce sie stad wypisac, to osobna sprawa i osobna sciezka.</p>
     */
    public static OperationNotAllowedException ownAccount() {
        return new OperationNotAllowedException(
            "Administrator nie moze usunac wlasnego konta", "error.user.selfdelete");
    }

    /** Format terminu w komunikacie - ten sam w obu jezykach, zeby nie bylo watpliwosci. */
    private static final java.time.format.DateTimeFormatter DEADLINE_FORMAT =
        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Proba opublikowania czegos mimo obowiazujacego zakazu.
     *
     * <p>Termin konca kary wedruje w {@code arguments}, zeby komunikat mogl go
     * pokazac. Podanie samego "nie wolno" byloby dla ukaranego bezuzyteczne -
     * nie wiedzialby, czy wrocic za godzine, czy za tydzien.</p>
     *
     * <p><b>Date formatujemy TUTAJ, a nie w pliku z komunikatami.</b>
     * {@code MessageFormat} (uzywany przez {@code MessageSource}) potrafi
     * sformatowac {@code java.util.Date}, ale <b>nie</b> {@code LocalDateTime} -
     * przy zapisie {@code {0,date,...}} rzuca wyjatkiem w srodku obslugi bledu,
     * czyli dokladnie tam, gdzie nie ma juz komu go obsluzyc. Konczy sie to
     * odpowiedzia 500 zamiast czytelnego komunikatu. Gotowy tekst nie ma jak
     * sie na tym wylozyc.</p>
     */
    public static OperationNotAllowedException postingBanned(java.time.LocalDateTime until) {
        String deadline = until.format(DEADLINE_FORMAT);
        return new OperationNotAllowedException(
            "Konto ma zakaz publikowania do " + deadline, "error.post.banned", deadline);
    }

    public String getMessageKey() {
        return messageKey;
    }

    /** Wartosci do wstawienia w tresc komunikatu - dzis uzywa ich tylko zakaz publikowania. */
    public Object[] getArguments() {
        return arguments;
    }
}
