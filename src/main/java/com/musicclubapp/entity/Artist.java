package com.musicclubapp.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Artysta z katalogu Deezera - element ulubionych na profilu. */
@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identyfikator z Deezera. unique = true zaklada w bazie indeks unikalny - to on gwarantuje,
     * ze jeden wykonawca = jeden wiersz.
     */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Zdjecie z Deezera. Moze go nie byc - wtedy pokazujemy zastepczy kolor. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Gatunki - do przyblizania dopasowan miedzy ludzmi, ktorzy nie maja wspolnego ani jednego
     * wykonawcy. @ElementCollection zaklada osobna tabele artist_genres z kolumnami artist_id i
     * genre.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "artist_genres", joinColumns = @JoinColumn(name = "artist_id"))
    @Column(name = "genre", length = 60)
    private Set<String> genres = new HashSet<>();

    protected Artist() {
        // wymagany przez JPA
    }

    public Artist(String externalId, String name, String imageUrl) {
        this.externalId = externalId;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Set<String> getGenres() {
        return genres;
    }

    public void applyGenres(Set<String> created) {
        this.genres.clear();
        if (created != null) {
            this.genres.addAll(created);
        }
    }

    /** Rownosc po externalId, a nie po id. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Artist artist)) {
            return false;
        }
        return externalId != null && externalId.equals(artist.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(externalId);
    }
}
