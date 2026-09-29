package com.musicclubapp.entity;

/** Do czego sluzy link z wiadomosci. */
public enum TokenPurpose {

    /** Potwierdzenie adresu: nowe konto albo nowy adres przy zmianie. */
    VERIFY,

    /** Zgoda ze STAREGO adresu na zmiane na nowy - albo "to nie ja". */
    APPROVE_CHANGE,

    /** Ustawienie nowego hasla bez znajomosci starego. */
    PASSWORD_RESET
}
