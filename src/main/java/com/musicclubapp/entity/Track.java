package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Utwor z katalogu Deezera - element ulubionych na profilu.
 *
 * <p>Zasada ta sama co przy {@link Artist}: <b>tylko z katalogu, nigdy
 * z klawiatury</b>. Dzieki temu ten sam utwor u dwoch osob to ten sam wiersz
 * w bazie, a nie dwa podobnie wygladajace teksty.</p>
 *
 * <p><b>Po co osobne pole {@code artistExternalId}</b>, skoro jest juz
 * {@code artistName}? Bo utwor jest tez <i>slabszym sygnalem o artyscie</i>.
 * Ktos moze nie miec Radiohead wsrod ulubionych wykonawcow, ale miec trzy ich
 * piosenki wsrod ulubionych utworow - i to tez o czyms swiadczy. Identyfikator
 * pozwala to policzyc bez porownywania tekstow.</p>
 *
 * <p>Nie kopiujemy tu okladki jako pliku - trzymamy sam adres. Zdjecie zostaje
 * na serwerach Deezera, a my nie zajmujemy sie ani miejscem na dysku, ani
 * prawami do obrazkow.</p>
 */
@Entity
@Table(name = "tracks")
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identyfikator utworu z Deezera - jeden utwor = jeden wiersz. */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "artist_name", nullable = false, length = 200)
    private String artistName;

    /** Identyfikator wykonawcy w Deezerze - patrz opis klasy. */
    @Column(name = "artist_external_id", length = 64)
    private String artistExternalId;

    /** Okladka albumu. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    protected Track() {
        // wymagany przez JPA
    }

    public Track(String externalId, String title, String artistName,
                 String artistExternalId, String imageUrl) {
        this.externalId = externalId;
        this.title = title;
        this.artistName = artistName;
        this.artistExternalId = artistExternalId;
        this.imageUrl = imageUrl;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getArtistExternalId() {
        return artistExternalId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    /** Rownosc po {@code externalId} - z tego samego powodu co w {@link Artist}. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Track track)) {
            return false;
        }
        return externalId != null && externalId.equals(track.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(externalId);
    }
}
