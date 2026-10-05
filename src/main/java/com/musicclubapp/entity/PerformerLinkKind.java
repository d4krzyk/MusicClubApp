package com.musicclubapp.entity;

import java.util.Locale;

/** Rodzaj linku wykonawcy (strona, profile w serwisach) - kolejnosc to kolejnosc na stronie wydarzenia. */
public enum PerformerLinkKind {
    HOMEPAGE,
    SPOTIFY,
    YOUTUBE,
    INSTAGRAM,
    FACEBOOK,
    TIKTOK,
    BANDCAMP,
    SOUNDCLOUD,
    WIKI;

    /**
     * Klucz z {@code externalLinks} Ticketmastera. Twitter, iTunes, Last.fm i MusicBrainz pomijamy (null) - pierwsze dwa
     * nie mowia nic o muzyce, Last.fm i tak jest w opisie, a MusicBrainz to identyfikator, nie strona.
     */
    public static PerformerLinkKind fromTicketmaster(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "homepage" -> HOMEPAGE;
            case "spotify" -> SPOTIFY;
            case "youtube" -> YOUTUBE;
            case "instagram" -> INSTAGRAM;
            case "facebook" -> FACEBOOK;
            case "tiktok" -> TIKTOK;
            case "bandcamp" -> BANDCAMP;
            case "soundcloud" -> SOUNDCLOUD;
            case "wiki" -> WIKI;
            default -> null;
        };
    }
}
