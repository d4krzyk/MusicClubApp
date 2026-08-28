package com.musicclubapp.entity;

/**
 * Za co mozna kogos zglosic.
 *
 * <p><b>Zamknieta lista, a nie samo pole tekstowe</b> - i to jest decyzja
 * na korzysc administratora. Przy wolnym tekscie kazde zgloszenie trzeba
 * przeczytac, zeby w ogole wiedziec, czego dotyczy; przy liscie widac to
 * z jednego rzutu oka i da sie posortowac. Opis slowny i tak jest, ale
 * <i>obok</i> powodu, a nie zamiast niego.</p>
 *
 * <p>Powody odpowiadaja temu, co w praktyce lamie zwykla netykiete -
 * nie sa proba spisania wszystkich mozliwych przewinien. Od tego jest
 * {@link #OTHER}.</p>
 */
public enum ReportReason {

    /** Obrazanie, grozby, uporczywe nagabywanie - najczesciej w wiadomosciach. */
    HARASSMENT,

    /** Reklamy, powtarzane linki, zasmiecanie tablicy. */
    SPAM,

    /** Tresci nienawistne wobec grupy albo osoby. */
    HATE,

    /** Tresci nieodpowiednie: wulgarne, drastyczne, nie na temat serwisu. */
    INAPPROPRIATE,

    /**
     * Podszywanie sie pod kogos innego.
     *
     * <p>Osobny powod od {@link #INAPPROPRIATE}, bo wymaga zupelnie innego
     * sprawdzenia: nie chodzi o to, CO ktos napisal, tylko za KOGO sie podaje.</p>
     */
    IMPERSONATION,

    /** Cokolwiek innego - wtedy liczy sie opis slowny. */
    OTHER
}
