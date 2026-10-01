package com.musicclubapp.entity;

/** Co administrator robi z kontem albo postem w chwili zamykania zgloszenia. */
public enum ModerationAction {

    /** Zapisujemy decyzje i na tym koniec. */
    NONE(null),

    /** Kasuje zglaszany post. */
    DELETE_POST(null),

    /** Kasuje zglaszany komentarz (razem z odpowiedziami pod nim). */
    DELETE_COMMENT(null),

    /** Zakaz publikowania - na podana liczbe godzin albo bezterminowo. */
    BAN_POSTING(BanKind.POSTING),

    /** Zakaz wysylania wiadomosci - osobna kara od zakazu publikowania. */
    BAN_MESSAGING(BanKind.MESSAGING),

    /** Kasuje konto razem z jego postami, zdjeciami i znajomosciami. */
    DELETE_ACCOUNT(null);

    /** Rodzaj kary, jesli to dzialanie jest kara - inaczej null. */
    private final BanKind banKind;

    ModerationAction(BanKind banKind) {
        this.banKind = banKind;
    }

    /** Rodzaj kary albo {@code null}, gdy to dzialanie kara nie jest. */
    public BanKind banKind() {
        return banKind;
    }
}
