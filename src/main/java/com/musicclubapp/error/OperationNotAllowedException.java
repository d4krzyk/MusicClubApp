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

    /** Kraj, z ktorego wydarzen nie pobieramy. */
    public static OperationNotAllowedException eventCountry() {
        return new OperationNotAllowedException(
            "Z tego kraju nie pobieramy wydarzen", "error.event.country");
    }

    /** Link z wiadomosci jest zly, zuzyty albo przeterminowany. */
    public static OperationNotAllowedException emailTokenInvalid() {
        return new OperationNotAllowedException(
            "Link potwierdzajacy jest niewazny", "error.email.token.invalid");
    }

    /** Potwierdzany adres zajal w miedzyczasie ktos inny. */
    public static OperationNotAllowedException emailTaken() {
        return new OperationNotAllowedException(
            "Adres zajety przez inne konto", "error.email.taken");
    }

    /** Adres jest juz potwierdzony - ponowna wysylka nie ma sensu. */
    public static OperationNotAllowedException emailAlreadyVerified() {
        return new OperationNotAllowedException(
            "Adres jest juz potwierdzony", "error.email.already.verified");
    }

    /** Nie ma zmiany adresu, ktora mozna by potwierdzic albo anulowac. */
    public static OperationNotAllowedException noPendingEmail() {
        return new OperationNotAllowedException(
            "Brak zmiany adresu do potwierdzenia", "error.email.no.pending");
    }

    /** Proba zablokowania samego siebie. */
    public static OperationNotAllowedException blockSelf() {
        return new OperationNotAllowedException("Nie mozna zablokowac samego siebie", "error.block.self");
    }

    /** Profil tylko dla znajomych albo zablokowany - szczegolow nie pokazujemy. */
    public static OperationNotAllowedException profilePrivate() {
        return new OperationNotAllowedException("Profil niedostepny dla ogladajacego", "error.profile.private");
    }

    /**
     * Tej osoby nie mozna zaprosic: nie przyjmuje zaproszen, nie macie
     * wspolnych znajomych albo jest blokada. Jeden komunikat na wszystko -
     * zeby zablokowany nie dowiedzial sie o blokadzie.
     */
    public static OperationNotAllowedException cannotInvite() {
        return new OperationNotAllowedException("Nie mozna zaprosic tej osoby", "error.friend.cannot.invite");
    }

    /** Serwer nie ma kluczy VAPID - powiadomien push nie da sie wlaczyc. */
    public static OperationNotAllowedException pushDisabled() {
        return new OperationNotAllowedException(
            "Powiadomienia push sa wylaczone na tym serwerze", "error.push.disabled");
    }

    /** Adres spoza uslug push przegladarek albo uszkodzone klucze subskrypcji. */
    public static OperationNotAllowedException pushInvalid() {
        return new OperationNotAllowedException(
            "Niepoprawna subskrypcja push", "error.push.invalid");
    }

    /** Proba powiadomienia probnego bez zadnego zapisanego urzadzenia. */
    public static OperationNotAllowedException pushNoDevices() {
        return new OperationNotAllowedException(
            "Brak urzadzen z wlaczonymi powiadomieniami", "error.push.noDevices");
    }

    /* ------------------------------------------------------------------ */
    /*  Klany                                                              */
    /* ------------------------------------------------------------------ */

    public static OperationNotAllowedException clanNameInvalid() {
        return new OperationNotAllowedException("Niepoprawna nazwa klanu", "error.clan.name.invalid");
    }

    public static OperationNotAllowedException clanNameReserved() {
        return new OperationNotAllowedException("Zarezerwowana nazwa klanu", "error.clan.name.reserved");
    }

    public static OperationNotAllowedException clanNameTaken() {
        return new OperationNotAllowedException("Nazwa klanu zajeta", "error.clan.name.taken");
    }

    public static OperationNotAllowedException clanTagInvalid() {
        return new OperationNotAllowedException("Niepoprawny skrot klanu", "error.clan.tag.invalid");
    }

    public static OperationNotAllowedException clanTagTaken() {
        return new OperationNotAllowedException("Skrot klanu zajety", "error.clan.tag.taken");
    }

    /** Ta osoba jest juz w klanie - jeden klan na osobe. */
    public static OperationNotAllowedException clanAlreadyMember() {
        return new OperationNotAllowedException("Jestes juz w klanie", "error.clan.alreadyMember");
    }

    /** Tylko dla czlonkow klanu (i administratora aplikacji). */
    public static OperationNotAllowedException clanNotMember() {
        return new OperationNotAllowedException("Tylko dla czlonkow klanu", "error.clan.notMember");
    }

    public static OperationNotAllowedException clanNotManager() {
        return new OperationNotAllowedException("Tylko zalozyciel i administratorzy klanu", "error.clan.notManager");
    }

    public static OperationNotAllowedException clanNotFounder() {
        return new OperationNotAllowedException("Tylko zalozyciel klanu", "error.clan.notFounder");
    }

    public static OperationNotAllowedException clanFull(int max) {
        return new OperationNotAllowedException("Klan jest pelny", "error.clan.full", max);
    }

    public static OperationNotAllowedException clanInviteeInClan() {
        return new OperationNotAllowedException("Ta osoba jest juz w klanie", "error.clan.inviteeInClan");
    }

    public static OperationNotAllowedException clanAlreadyInvited() {
        return new OperationNotAllowedException("Ta osoba jest juz zaproszona", "error.clan.alreadyInvited");
    }

    public static OperationNotAllowedException clanTooManyInvites(int max) {
        return new OperationNotAllowedException("Za duzo oczekujacych zaproszen", "error.clan.tooManyInvites", max);
    }

    /** Zalozyciel nie moze po prostu odejsc - klan zostalby bez wlasciciela. */
    public static OperationNotAllowedException clanFounderMustTransfer() {
        return new OperationNotAllowedException("Najpierw przekaz klan", "error.clan.founderMustTransfer");
    }

    public static OperationNotAllowedException clanCannotKick() {
        return new OperationNotAllowedException("Nie mozna wyrzucic tej osoby", "error.clan.cannotKick");
    }

    /** Serwer nie wysyla poczty - nie ma jak dostarczyc linku. */
    public static OperationNotAllowedException mailDisabled() {
        return new OperationNotAllowedException(
            "Poczta wylaczona na tym serwerze", "error.mail.disabled");
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
