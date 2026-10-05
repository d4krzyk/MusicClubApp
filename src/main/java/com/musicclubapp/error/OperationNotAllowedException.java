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

    public static OperationNotAllowedException clanTrackInvalid() {
        return new OperationNotAllowedException("Podaj link do utworu", "error.clan.track.invalid");
    }

    public static OperationNotAllowedException clanTrackLimit(int max) {
        return new OperationNotAllowedException("Limit propozycji w tygodniu", "error.clan.track.limit", max);
    }

    public static OperationNotAllowedException clanTrackDuplicate() {
        return new OperationNotAllowedException("Ten utwor juz jest zaproponowany", "error.clan.track.duplicate");
    }

    /** Glosowanie nad utworem z minionego tygodnia jest zamkniete. */
    public static OperationNotAllowedException clanTrackClosed() {
        return new OperationNotAllowedException("Glosowanie zamkniete", "error.clan.track.closed");
    }

    /** Zgloszenie klanu idzie osobna sciezka - zwykle zgloszenie z kontekstem CLAN nie wie, ktorego klanu dotyczy. */
    public static OperationNotAllowedException reportClanEndpoint() {
        return new OperationNotAllowedException("Klan zglasza sie przez strone klanu", "error.report.clanEndpoint");
    }

    /** Klan nie przyjmuje prosb - ani z wyboru, ani przez blokade; nie mowimy, ktore z nich. */
    public static OperationNotAllowedException clanNoRequests() {
        return new OperationNotAllowedException("Ten klan nie przyjmuje teraz prosb", "error.clan.noRequests");
    }

    public static OperationNotAllowedException clanRequestAlready() {
        return new OperationNotAllowedException("Prosba juz czeka", "error.clan.request.already");
    }

    public static OperationNotAllowedException clanTooManyRequests(int max) {
        return new OperationNotAllowedException("Za duzo prosb", "error.clan.request.tooMany", max);
    }

    public static OperationNotAllowedException clanInvitedAlready() {
        return new OperationNotAllowedException("Masz zaproszenie", "error.clan.request.invited");
    }

    public static OperationNotAllowedException clanGenreInvalid() {
        return new OperationNotAllowedException("Niepoprawny gatunek", "error.clan.genre.invalid");
    }

    public static OperationNotAllowedException clanCityInvalid() {
        return new OperationNotAllowedException("Niepoprawne miasto", "error.clan.city.invalid");
    }

    public static OperationNotAllowedException clanTitleInvalid() {
        return new OperationNotAllowedException("Niepoprawny tytul", "error.clan.title.invalid");
    }

    public static OperationNotAllowedException clanTitleTaken() {
        return new OperationNotAllowedException("Taki tytul juz jest", "error.clan.title.taken");
    }

    public static OperationNotAllowedException clanTitleLimit(int max) {
        return new OperationNotAllowedException("Za duzo tytulow", "error.clan.title.limit", max);
    }

    public static OperationNotAllowedException clanTitleAutoRule() {
        return new OperationNotAllowedException("Tytul automatyczny wymaga reguly", "error.clan.title.autoRule");
    }

    public static OperationNotAllowedException clanTitleNotAssignable() {
        return new OperationNotAllowedException("Tego tytulu nie nadaje sie recznie", "error.clan.title.notAssignable");
    }

    public static OperationNotAllowedException clanTitleNotSelf() {
        return new OperationNotAllowedException("Tego tytulu nie mozna wziac samemu", "error.clan.title.notSelf");
    }

    public static OperationNotAllowedException clanTitleSelfLimit(int max) {
        return new OperationNotAllowedException("Za duzo wlasnych tytulow", "error.clan.title.selfLimit", max);
    }

    public static OperationNotAllowedException clanTitleMemberLimit(int max) {
        return new OperationNotAllowedException("Za duzo tytulow u jednej osoby", "error.clan.title.memberLimit", max);
    }

    public static OperationNotAllowedException clanPollInvalid() {
        return new OperationNotAllowedException("Niepoprawna ankieta", "error.clan.poll.invalid");
    }

    public static OperationNotAllowedException clanPollLimit(int max) {
        return new OperationNotAllowedException("Za duzo otwartych ankiet", "error.clan.poll.limit", max);
    }

    /** Spotkanie z niepoprawnym czasem, punktem albo przypomnieniem - klucz mowi, co jest nie tak. */
    public static OperationNotAllowedException meetingInvalid(String messageKey) {
        return new OperationNotAllowedException("Niepoprawne spotkanie", messageKey);
    }

    public static OperationNotAllowedException meetingLimit(int max) {
        return new OperationNotAllowedException("Za duzo nadchodzacych spotkan", "error.meeting.limit", max);
    }

    /** Spotkanie odwolane albo juz zakonczone - nie ma na co odpowiadac. */
    public static OperationNotAllowedException meetingClosed() {
        return new OperationNotAllowedException("Spotkanie jest zamkniete", "error.meeting.closed");
    }

    /** Odpowiadac moga strony rozmowy, ktore sa znajomymi, i czlonkowie klanu. */
    public static OperationNotAllowedException meetingCannotRespond() {
        return new OperationNotAllowedException("Nie mozna odpowiedziec na to spotkanie", "error.meeting.cannotRespond");
    }

    public static OperationNotAllowedException meetingNotCreator() {
        return new OperationNotAllowedException("Spotkanie odwoluje tylko zakladajacy", "error.meeting.notCreator");
    }

    public static OperationNotAllowedException clanPollClosed() {
        return new OperationNotAllowedException("Ankieta zamknieta", "error.clan.poll.closed");
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

    /** Proba skasowania cudzego komentarza (nie jest sie ani autorem, ani autorem posta, ani moderatorem). */
    public static OperationNotAllowedException someoneElsesComment() {
        return new OperationNotAllowedException(
            "Mozna usuwac tylko wlasne komentarze albo komentarze pod wlasnym postem", "error.comment.notowner");
    }

    /** Zgloszenie komentarza bez wskazania komentarza. */
    public static OperationNotAllowedException reportNeedsComment() {
        return new OperationNotAllowedException(
            "Wskaz komentarz, ktorego dotyczy zgloszenie", "error.report.needscomment");
    }

    /** Proba skasowania komentarza przy zgloszeniu, ktore komentarza nie dotyczy. */
    public static OperationNotAllowedException reportHasNoComment() {
        return new OperationNotAllowedException(
            "To zgloszenie nie dotyczy zadnego komentarza", "error.report.action.nocomment");
    }

    /* --- Karta profilu i tryb Poznawaj --- */

    /** To samo pytanie muzyczne dwa razy na jednej karcie. */
    public static OperationNotAllowedException cardPromptTwice() {
        return new OperationNotAllowedException("To samo pytanie dwa razy", "error.card.promptTwice");
    }

    /** Galeria jest pelna. */
    public static OperationNotAllowedException cardPhotoLimit(int max) {
        return new OperationNotAllowedException("Galeria jest pelna", "error.card.photoLimit", max);
    }

    /** Nowa kolejnosc zdjec nie obejmuje dokladnie wszystkich zdjec z galerii. */
    public static OperationNotAllowedException cardPhotoOrder() {
        return new OperationNotAllowedException("Kolejnosc nie pasuje do galerii", "error.card.photoOrder");
    }

    /** Talia i decyzje tylko z wlaczonym trybem Poznawaj. */
    public static OperationNotAllowedException discoverOff() {
        return new OperationNotAllowedException("Tryb Poznawaj jest wylaczony", "error.discover.off");
    }

    /** Zasieg spoza listy. */
    public static OperationNotAllowedException discoverRadius() {
        return new OperationNotAllowedException("Niedozwolony zasieg", "error.discover.radius");
    }

    /** Ta osoba juz nie jest w talii (wylaczyla tryb, zablokowala, juz jestescie znajomymi). */
    public static OperationNotAllowedException discoverGone() {
        return new OperationNotAllowedException("Tej osoby nie ma juz w talii", "error.discover.gone");
    }

    /** Nie ma czego cofnac (ostatnia decyzja jest za stara albo dala znajomosc). */
    public static OperationNotAllowedException discoverNothingToUndo() {
        return new OperationNotAllowedException("Nie ma czego cofnac", "error.discover.undo");
    }

    /** Wiadomosc na czacie klanu bez tekstu i bez GIF-a (walidacja zapytania lapie to wczesniej - to zapas). */
    public static OperationNotAllowedException emptyClanMessage() {
        return new OperationNotAllowedException(
            "Wiadomosc nie ma tresci ani GIF-a", "validation.clanMessage.empty");
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
