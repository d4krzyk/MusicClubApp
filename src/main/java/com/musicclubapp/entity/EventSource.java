package com.musicclubapp.entity;

/**
 * Skad wiemy o wydarzeniu. Ticketmaster jest glownym zrodlem (najpelniejsze dane, bilety, ceny); pozostale dokladaja
 * koncerty, ktorych Ticketmaster nie ma, i uzupelniaja braki w tych, ktore ma.
 */
public enum EventSource {
    TICKETMASTER("Ticketmaster", null),
    BANDSINTOWN("Bandsintown", "bandsintown:"),
    SONGKICK("Songkick", "songkick:");

    private final String label;
    private final String prefix;

    EventSource(String label, String prefix) {
        this.label = label;
        this.prefix = prefix;
    }

    /** Nazwa do podpisu "Dane o wydarzeniu: ...". */
    public String label() {
        return label;
    }

    /**
     * {@code music_events.external_id} wydarzenia zalozonego przez to zrodlo - z przedrostkiem, zeby numery roznych
     * serwisow sie nie zderzyly. Ticketmaster pisze swoje numery bez przedrostka (tak jak od V2).
     */
    public String externalId(String id) {
        return prefix == null ? id : prefix + id;
    }
}
