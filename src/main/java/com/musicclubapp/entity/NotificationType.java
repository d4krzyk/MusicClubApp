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
    EVENT_REMINDER,

    /** Ktos zaprosil mnie do klanu. Prowadzi na strone klanu z przyciskami przyjecia i odmowy. */
    CLAN_INVITE,

    /** Zostalem wyrzucony z klanu. Bez sprawcy - klan nie mowi, kto to zrobil. */
    CLAN_KICKED,
    /** Ktos prosi o dolaczenie do mojego klanu - dla zarzadu klanu. Prowadzi na strone klanu. */
    CLAN_JOIN_REQUEST,
    /** Moja prosba o dolaczenie do klanu zostala przyjeta. Bez sprawcy. */
    CLAN_REQUEST_ACCEPTED,
    /** Ktos skomentowal moj post. Prowadzi do komentarza. */
    POST_COMMENT,
    /** Ktos odpowiedzial na moj komentarz (albo odpowiedz). */
    COMMENT_REPLY,
    /** Ktos oznaczyl mnie w komentarzu (@login). */
    COMMENT_MENTION
}
