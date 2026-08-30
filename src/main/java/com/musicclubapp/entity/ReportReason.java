package com.musicclubapp.entity;

/** Za co mozna kogos zglosic. */
public enum ReportReason {

    /** Obrazanie, grozby, uporczywe nagabywanie - najczesciej w wiadomosciach. */
    HARASSMENT,

    /** Reklamy, powtarzane linki, zasmiecanie tablicy. */
    SPAM,

    /** Tresci nienawistne wobec grupy albo osoby. */
    HATE,

    /** Tresci nieodpowiednie: wulgarne, drastyczne, nie na temat serwisu. */
    INAPPROPRIATE,

    /** Podszywanie sie pod kogos innego. */
    IMPERSONATION,

    /** Cokolwiek innego - wtedy liczy sie opis slowny. */
    OTHER
}
