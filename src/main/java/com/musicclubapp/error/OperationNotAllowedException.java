package com.musicclubapp.error;

/** Operacja jest technicznie mozliwa, ale zabroniona przez regule biznesowa. */
public class OperationNotAllowedException extends RuntimeException {

    private final String messageKey;
    private final Object[] arguments;

    private OperationNotAllowedException(String message, String messageKey, Object... arguments) {
        super(message);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    /** Administrator nie moze zmienic wlasnej roli. */
    public static OperationNotAllowedException ownRole() {
        return new OperationNotAllowedException(
            "Administrator nie moze zmienic wlasnej roli", "error.role.self");
    }

    /** Proba usuniecia posta, ktorego sie nie jest autorem (i nie jest sie adminem). */
    public static OperationNotAllowedException someoneElsesPost() {
        return new OperationNotAllowedException(
            "Mozna usuwac tylko wlasne posty", "error.post.notowner");
    }

    /** Proba edycji cudzego posta. */
    public static OperationNotAllowedException someoneElsesPostEdit() {
        return new OperationNotAllowedException(
            "Mozna edytowac tylko wlasne posty", "error.post.notauthor");
    }

    /** Proba siegniecia po post przeznaczony tylko dla znajomych autora. */
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

    /** Proba przyjecia albo odrzucenia cudzego zaproszenia. */
    public static OperationNotAllowedException someoneElsesInvitation() {
        return new OperationNotAllowedException(
            "To nie jest Twoje zaproszenie", "error.friend.notyours");
    }

    /** Osiagnieto gorny limit ulubionych. */
    public static OperationNotAllowedException favoritesLimit() {
        return new OperationNotAllowedException(
            "Osiagnieto limit ulubionych", "error.favorite.limit");
    }

    /** Pozycji nie ma w katalogu Deezera. */
    public static OperationNotAllowedException notInCatalog() {
        return new OperationNotAllowedException(
            "Takiej pozycji nie ma w katalogu", "error.favorite.notincatalog");
    }

    /** Gablotka playlist jest pelna - piec pozycji to jej caly sens. */
    public static OperationNotAllowedException playlistLimit() {
        return new OperationNotAllowedException(
            "Gablotka playlist jest pelna", "error.playlist.limit");
    }

    /** Wklejony adres prowadzi gdzie indziej niz do playlisty. */
    public static OperationNotAllowedException notAPlaylist() {
        return new OperationNotAllowedException(
            "To nie jest link do playlisty", "error.playlist.notplaylist");
    }

    /** Ta playlista juz jest w gablotce. */
    public static OperationNotAllowedException playlistAlreadyThere() {
        return new OperationNotAllowedException(
            "Ta playlista juz jest w gablotce", "error.playlist.duplicate");
    }

    /** Zapis na wydarzenie, ktore juz sie odbylo. */
    public static OperationNotAllowedException eventPast() {
        return new OperationNotAllowedException(
            "Wydarzenie juz sie odbylo", "error.event.past");
    }

    /** Zapis na wydarzenie, ktorego Ticketmaster juz nie ma. */
    public static OperationNotAllowedException eventWithdrawn() {
        return new OperationNotAllowedException(
            "Wydarzenia nie ma juz w Ticketmasterze", "error.event.withdrawn");
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

    /** Administrator nie moze skasowac wlasnego konta z panelu. */
    public static OperationNotAllowedException ownAccount() {
        return new OperationNotAllowedException(
            "Administrator nie moze usunac wlasnego konta", "error.user.selfdelete");
    }

    /** Proba napisania do kogos, kto nie jest znajomym. */
    public static OperationNotAllowedException messageToStranger() {
        return new OperationNotAllowedException(
            "Pisac mozna tylko ze znajomymi", "error.message.notfriend");
    }

    /** Proba wyslania wiadomosci do samego siebie. */
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

    /** Zgloszenie na te osobe juz czeka na decyzje. */
    public static OperationNotAllowedException reportAlreadyOpen() {
        return new OperationNotAllowedException(
            "Zgloszenie na te osobe juz czeka na decyzje", "error.report.duplicate");
    }

    /** Wyczerpany dzienny limit zgloszen. */
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

    /** Proba skasowania posta przy zgloszeniu, ktore posta nie dotyczy. */
    public static OperationNotAllowedException reportHasNoPost() {
        return new OperationNotAllowedException(
            "To zgloszenie nie dotyczy zadnego posta", "error.report.action.nopost");
    }

    /** Proba ponownego otwarcia sprawy, ktora i tak jest otwarta. */
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

    /** Administrator probuje zablokowac adres, z ktorego sam wlasnie jest. */
    public static OperationNotAllowedException ownAddress() {
        return new OperationNotAllowedException(
            "Nie mozesz zablokowac adresu, z ktorego wlasnie korzystasz",
            "error.ip.self");
    }

    /** Proba zrobienia czegos mimo obowiazujacej kary. */
    public static OperationNotAllowedException banned(
            com.musicclubapp.entity.BanKind kind, java.time.LocalDateTime until) {

        boolean forever = com.musicclubapp.entity.User.isForever(until);
        return new OperationNotAllowedException(
            "Konto ma kare: " + kind,
            kind.messageKey(forever),
            // Przy karze bezterminowej nie ma czego pokazywac - stad brak terminu
            forever ? new Object[0] : new Object[] { until });
    }

    /** Termin konca kary, jesli ten blad go dotyczy - inaczej null. */
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
