package com.musicclubapp.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gatunki wykonawcy z koncertu, sprawdzone w Last.fm.
 *
 * Ticketmaster podaje tylko ogolne etykiety ("Rock", "Hip-Hop/Rap"), a przy
 * wielu polskich wydarzeniach "Undefined". Tagi Last.fm sa dokladniejsze
 * ("indie rock", "polish hip-hop") i pochodza z tego samego zrodla co
 * gatunki ulubionych artystow - wiec da sie je z nimi porownac.
 *
 * Kluczem jest nazwa w postaci do porownywania, nie identyfikator
 * Ticketmastera: ten sam zespol bywa tam pod kilkoma identyfikatorami.
 */
@Entity
@Table(name = "performer_tags")
public class PerformerTags {

    @Id
    @Column(name = "name_key", length = 200)
    private String nameKey;

    /** Nazwa, o ktora pytalismy Last.fm - do podgladu w bazie. */
    @Column(nullable = false, length = 200)
    private String name;

    /** Kiedy pytalismy. Po dwoch miesiacach pytamy znowu - tagi sie zmieniaja. */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "performer_tag_genres", joinColumns = @JoinColumn(name = "name_key"))
    @Column(name = "genre", length = 60)
    private Set<String> genres = new LinkedHashSet<>();

    protected PerformerTags() {
        // wymagany przez JPA
    }

    public PerformerTags(String nameKey, String name) {
        this.nameKey = nameKey;
        this.name = name;
    }

    public void update(Set<String> found, LocalDateTime when) {
        genres.clear();
        genres.addAll(found);
        checkedAt = when;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public Set<String> getGenres() {
        return genres;
    }
}
