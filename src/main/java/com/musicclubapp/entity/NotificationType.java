package com.musicclubapp.entity;

/** Rodzaj powiadomienia - decyduje o tresci komunikatu i o tym, dokad prowadzi klikniecie. */
public enum NotificationType {

    /** Ktos zareagowal na moj post. */
    REACTION,

    /** Ktos wyslal mi zaproszenie do znajomych. Prowadzi na strone znajomych. */
    FRIEND_REQUEST,

    /**
     * Ktos przyjal moje zaproszenie (albo zaprosil mnie, gdy ja juz zaprosilem jego - wtedy
     * znajomosc powstaje od razu).
     */
    FRIEND_ACCEPTED,

    /** Ktos zglosil uzytkownika - powiadomienie WYLACZNIE dla administratorow. */
    REPORT,

    /** Moje zgloszenie zostalo rozpatrzone - powiadomienie dla ZGLASZAJACEGO. */
    REPORT_RESOLVED,

    /** Wydarzenie, na ktore jestem zapisany, jest za kilka dni. Bez sprawcy - pisze aplikacja. */
    EVENT_REMINDER
}
