package com.musicclubapp.entity;

/** Rola w klanie: zalozyciel, administrator klanu (moze wyrzucac) albo zwykly czlonek. */
public enum ClanRole {
    FOUNDER,
    ADMIN,
    MEMBER;

    /** Zalozyciel i administratorzy zarzadzaja klanem: wyrzucaja czlonkow, zmieniaja opis i zdjecia. */
    public boolean manages() {
        return this != MEMBER;
    }
}
