package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Opis wykonawcy od Ticketmastera - pola {@code description} / {@code additionalInfo} wykonawcy (na stronie artysty
 * w ticketmaster.pl sekcja "About"). Jeden na nazwe sprowadzona przez {@code NameKeys}, jak linki. Pusty opis znaczy
 * "Ticketmaster nic o nim nie ma" - tez zapamietane, zeby nie pytac przy kazdym "Kim jest?".
 */
@Entity
@Table(name = "performer_about")
public class PerformerAbout {

    public static final int MAX_ABOUT = 2000;

    @Id
    @Column(name = "name_key", length = 200)
    private String nameKey;

    /** Numer wykonawcy u Ticketmastera, z ktorego opis przyszedl. */
    @Column(name = "attraction_id", length = 64)
    private String attractionId;

    @Column(length = MAX_ABOUT)
    private String about;

    /** Jezyk opisu ("pl", "en") - z {@code locale} wykonawcy. */
    @Column(length = 8)
    private String lang;

    /** Strona artysty u Ticketmastera - do przypisania zrodla. */
    @Column(name = "page_url", length = 500)
    private String pageUrl;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    protected PerformerAbout() {
        // wymagany przez JPA
    }

    public PerformerAbout(String nameKey) {
        this.nameKey = nameKey;
    }

    /** Nowa odpowiedz Ticketmastera; {@code about == null} = opisu nie ma. */
    public void update(String attractionId, String about, String lang, String pageUrl, LocalDateTime now) {
        if (attractionId != null) {
            this.attractionId = attractionId;
        }
        this.about = about;
        this.lang = about == null ? null : lang;
        this.pageUrl = about == null ? null : pageUrl;
        this.checkedAt = now;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getAbout() {
        return about;
    }

    public String getLang() {
        return lang;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }
}
