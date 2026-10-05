package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * "Kim jest" wykonawca - krotki opis, sluchacze i podobni z Last.fm, zapamietane na kilka tygodni, zeby strona wydarzenia
 * nie pytala Last.fm przy kazdym otwarciu. Osobno na jezyk (Last.fm ma opisy po polsku dla czesci wykonawcow).
 * To dane o wykonawcach, nie o uzytkownikach.
 */
@Entity
@Table(name = "artist_profiles",
    uniqueConstraints = @UniqueConstraint(name = "uk_artist_profile", columnNames = {"name_key", "lang"}))
public class ArtistProfile {

    public static final int MAX_BIO = 1500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Column(nullable = false, length = 5)
    private String lang;

    /** Nazwa tak, jak poprawil ja Last.fm ("autocorrect"). */
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = MAX_BIO)
    private String bio;

    @Column(name = "bio_url", length = 500)
    private String bioUrl;

    private Long listeners;

    /** Podobni wykonawcy, po jednym w wierszu (najwyzej kilku). Nie "similar" - to slowo zastrzezone w PostgreSQL. */
    @Column(name = "similar_artists", length = 1000)
    private String similar;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected ArtistProfile() {
        // wymagany przez JPA
    }

    public ArtistProfile(String nameKey, String lang) {
        this.nameKey = nameKey;
        this.lang = lang;
    }

    public void update(String name, String bio, String bioUrl, Long listeners, List<String> similar, LocalDateTime now) {
        this.name = name;
        this.bio = bio;
        this.bioUrl = bioUrl;
        this.listeners = listeners;
        this.similar = similar == null || similar.isEmpty() ? null : String.join("\n", similar);
        this.fetchedAt = now;
    }

    public String getName() {
        return name;
    }

    public String getBio() {
        return bio;
    }

    public String getBioUrl() {
        return bioUrl;
    }

    public Long getListeners() {
        return listeners;
    }

    public List<String> getSimilar() {
        return similar == null ? List.of() : Arrays.asList(similar.split("\n"));
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
