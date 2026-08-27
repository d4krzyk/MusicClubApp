package com.musicclubapp.entity;

/**
 * Rodzaj powiadomienia - decyduje o tresci komunikatu i o tym, dokad
 * prowadzi klikniecie.
 *
 * <p><b>Kazde powiadomienie musi gdzies prowadzic.</b> Powiadomienie, po
 * ktorym nie da sie przejsc do rzeczy, ktora sie wydarzyla, zmusza
 * uzytkownika do samodzielnego szukania - a wtedy latwiej je zignorowac
 * niz obsluzyc. Dlatego kazda wartosc tego enuma ma przypisane miejsce
 * docelowe (patrz {@code NotificationMapper}).</p>
 */
public enum NotificationType {

    /**
     * Ktos zareagowal na moj post.
     *
     * <p>Prowadzi do <b>tego konkretnego posta</b>, a nie na tablice.
     * Post moze byc setny od gory - odeslanie na tablice znaczyloby
     * "poszukaj sobie".</p>
     */
    REACTION,

    /** Ktos wyslal mi zaproszenie do znajomych. Prowadzi na strone znajomych. */
    FRIEND_REQUEST,

    /**
     * Ktos przyjal moje zaproszenie (albo zaprosil mnie, gdy ja juz
     * zaprosilem jego - wtedy znajomosc powstaje od razu).
     *
     * <p>Prowadzi na profil tej osoby: skoro wlasnie zostalismy znajomymi,
     * najbardziej prawdopodobne jest, ze chce sie go obejrzec.</p>
     */
    FRIEND_ACCEPTED
}
