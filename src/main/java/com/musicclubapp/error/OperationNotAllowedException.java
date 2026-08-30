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

    /**
     * Proba napisania do kogos, kto nie jest znajomym.
     *
     * <p><b>To jest glowna blokada czatu.</b> Aplikacja sluzy do poznawania
     * ludzi, ale kolejnosc jest ustalona: najpierw zaproszenie, potem rozmowa.
     * Bez tego kazdy moglby pisac do kazdego, a serwis o wspolnym guscie
     * muzycznym zamienilby sie w skrzynke na zaczepki od obcych.</p>
     */
    public static OperationNotAllowedException messageToStranger() {
        return new OperationNotAllowedException(
            "Pisac mozna tylko ze znajomymi", "error.message.notfriend");
    }

    /**
     * Proba wyslania wiadomosci do samego siebie.
     *
     * <p>Osobny komunikat od powyzszego, bo to zupelnie co innego: nie jest to
     * proba obejscia blokady, tylko zwykla pomylka. "Pisac mozna tylko ze
     * znajomymi" byloby w tej sytuacji mylace - przeciez ze soba sie jest.</p>
     */
    public static OperationNotAllowedException messageToSelf() {
        return new OperationNotAllowedException(
            "Nie mozna napisac do samego siebie", "error.message.self");
    }

    /* ------------------------------------------------------------------ */
    /*  Zgloszenia                                                         */
    /* ------------------------------------------------------------------ */

    /** Proba zgloszenia samego siebie - zwykla pomylka, nie atak. */
    public static OperationNotAllowedException reportSelf() {
        return new OperationNotAllowedException(
            "Nie mozna zglosic samego siebie", "error.report.self");
    }

    /**
     * Zgloszenie na te osobe juz czeka na decyzje.
     *
     * <p>Pierwsza z dwoch blokad przed zasypywaniem panelu. Po zamknieciu
     * poprzedniego wolno zglosic ponownie - wtedy chodzi juz o NOWE zdarzenie.</p>
     */
    public static OperationNotAllowedException reportAlreadyOpen() {
        return new OperationNotAllowedException(
            "Zgloszenie na te osobe juz czeka na decyzje", "error.report.duplicate");
    }

    /**
     * Wyczerpany dzienny limit zgloszen.
     *
     * <p>Druga blokada. Bez niej jedna osoba moglaby zglosic po kolei
     * wszystkich w serwisie - kazde zgloszenie osobne, wiec ta pierwsza
     * nie zadzialalaby ani razu.</p>
     */
    public static OperationNotAllowedException reportLimit(int limit) {
        return new OperationNotAllowedException(
            "Wyczerpano dzienny limit zgloszen (" + limit + ")",
            "error.report.limit", limit);
    }

    /** Zgloszenie posta bez wskazania, ktorego. */
    public static OperationNotAllowedException reportNeedsPost() {
        return new OperationNotAllowedException(
            "Wskaz post, ktorego dotyczy zgloszenie", "error.report.nopost");
    }

    /** Wskazany post nie nalezy do osoby, ktora zglaszamy. */
    public static OperationNotAllowedException reportWrongAuthor() {
        return new OperationNotAllowedException(
            "Ten post nie nalezy do zglaszanej osoby", "error.report.wrongauthor");
    }

    /** Zgloszenie rozmowy, ktorej nie ma. */
    public static OperationNotAllowedException reportEmptyConversation() {
        return new OperationNotAllowedException(
            "Nie ma rozmowy, ktora mozna by dolaczyc", "error.report.noconversation");
    }

    /**
     * Proba skasowania posta przy zgloszeniu, ktore posta nie dotyczy.
     *
     * <p>Interfejs takiego wyboru nie pokazuje, ale zapytanie moze przyjsc
     * z dowolnego miejsca - a "skasuj post" bez posta musi skonczyc sie
     * czytelna odmowa, a nie bledem o pustej wartosci.</p>
     */
    public static OperationNotAllowedException reportHasNoPost() {
        return new OperationNotAllowedException(
            "To zgloszenie nie dotyczy zadnego posta", "error.report.action.nopost");
    }

    /**
     * Proba ponownego otwarcia sprawy, ktora i tak jest otwarta.
     *
     * <p>Nazwa celowo inna niz {@code reportAlreadyOpen()} kilka metod wyzej -
     * tamta mowi "na te osobe juz czeka zgloszenie" i dotyczy skladania
     * nowego, a ta dotyczy zmiany decyzji w istniejacym. Dwie rozne sytuacje
     * pod jedna nazwa byly by pomylka gotowa do popelnienia.</p>
     */
    public static OperationNotAllowedException reportNotClosed() {
        return new OperationNotAllowedException(
            "To zgloszenie jest juz otwarte", "error.report.open");
    }

    /** Ktos inny zdazyl zamknac to zgloszenie wczesniej. */
    public static OperationNotAllowedException reportAlreadyClosed() {
        return new OperationNotAllowedException(
            "To zgloszenie zostalo juz zamkniete", "error.report.closed");
    }

    /** "Zamykam jako otwarte" nie jest decyzja. */
    public static OperationNotAllowedException reportDecisionRequired() {
        return new OperationNotAllowedException(
            "Wybierz decyzje: zasadne albo bezpodstawne", "error.report.decision");
    }

    /* ------------------------------------------------------------------ */
    /*  Adresy sieciowe                                                    */
    /* ------------------------------------------------------------------ */

    /** Proba wejscia z zablokowanego adresu. */
    public static OperationNotAllowedException blockedAddress() {
        return new OperationNotAllowedException(
            "Ten adres zostal zablokowany", "error.ip.blocked");
    }

    /**
     * Administrator probuje zablokowac adres, z ktorego sam wlasnie jest.
     *
     * <p>Ta sama mysl co przy {@link #ownAccount()}: jedno klikniecie nie moze
     * zostawic portalu bez nikogo, kto ma do niego dostep. Przy testowaniu na
     * jednym komputerze to nie jest przypadek teoretyczny - administrator
     * i osoba blokowana siedza wtedy za tym samym adresem.</p>
     */
    public static OperationNotAllowedException ownAddress() {
        return new OperationNotAllowedException(
            "Nie mozesz zablokowac adresu, z ktorego wlasnie korzystasz",
            "error.ip.self");
    }

    /**
     * Proba zrobienia czegos mimo obowiazujacej kary.
     *
     * <p><b>Jedna fabryka na oba rodzaje kary.</b> Wczesniej byly dwie -
     * {@code postingBanned} i {@code messagingBanned} - rozniace sie
     * wylacznie kluczem komunikatu. Klucz nalezy do rodzaju kary, wiec
     * mieszka teraz przy {@link BanKind}, a nie w nazwie metody.</p>
     *
     * <p><b>Komunikat NIE zawiera daty</b> i to jest naprawa bledu. Wczesniej
     * serwer wklejal tutaj termin sformatowany u siebie i wychodzilo z tego
     * cos takiego: kara nalozona o 16:55 na godzine pokazywala sie ukaranemu
     * jako <i>„do 15:55"</i>, czyli w przeszlosci. Serwer liczy czas w UTC
     * i <b>nie wie, w jakiej strefie siedzi uzytkownik</b> - nie ma tez skad
     * tego wiedziec, bo jedna aplikacja obsluguje ludzi z roznych stref
     * naraz. Termin wedruje wiec jako <b>chwila</b> (pole {@code deadline}
     * w odpowiedzi), a napis sklada z niej przegladarka, juz w czasie
     * lokalnym.</p>
     *
     * <p>Zasada na przyszlosc: <b>serwer podaje chwile, klient robi z niej
     * godzine</b>.</p>
     */
    public static OperationNotAllowedException banned(
            com.musicclubapp.entity.BanKind kind, java.time.LocalDateTime until) {

        boolean forever = com.musicclubapp.entity.User.isForever(until);
        return new OperationNotAllowedException(
            "Konto ma kare: " + kind,
            kind.messageKey(forever),
            // Przy karze bezterminowej nie ma czego pokazywac - stad brak terminu
            forever ? new Object[0] : new Object[] { until });
    }

    /**
     * Termin konca kary, jesli ten blad go dotyczy - inaczej {@code null}.
     *
     * <p>Wyciagamy go z {@code arguments}, bo tylko tam moze sie znalezc.
     * Obsluga bledow wklada go do odpowiedzi jako osobne pole, zeby
     * przegladarka mogla go sformatowac w strefie uzytkownika.</p>
     */
    public java.time.LocalDateTime getDeadline() {
        for (Object argument : arguments) {
            if (argument instanceof java.time.LocalDateTime moment) {
                return moment;
            }
        }
        return null;
    }

    public String getMessageKey() {
        return messageKey;
    }

    /** Wartosci do wstawienia w tresc komunikatu - dzis uzywa ich tylko zakaz publikowania. */
    public Object[] getArguments() {
        return arguments;
    }
}
