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

/**
 * Artysta z katalogu Deezera - element ulubionych na profilu.
 *
 * <p><b>Dlaczego artysta nie jest zwyklym tekstem w profilu.</b> Gdyby kazdy
 * wpisywal nazwe recznie, w bazie wyladowaloby "Radiohead", "radiohead",
 * "Radiohed" i "Radiohead ", czyli cztery rozne byty. Dopasowanie ludzi po
 * wspolnych artystach przestaloby dzialac dokladnie wtedy, kiedy jest
 * potrzebne. Do tego kazdy moglby wpisac cokolwiek - a wymyslony artysta
 * to gotowe pole do popisu dla trolli.</p>
 *
 * <p><b>Dlatego jedynym zrodlem jest katalog Deezera.</b> Nie da sie dodac
 * artysty, ktorego tam nie ma. {@code externalId} to identyfikator z Deezera
 * i jest UNIKALNY - ten sam wykonawca ma w naszej bazie dokladnie jeden
 * wiersz, niezaleznie od tego, ilu uzytkownikow go polubilo.</p>
 *
 * <p><b>Encja jest WSPOLNA dla wszystkich uzytkownikow</b>, a nie kopiowana
 * do kazdego profilu. Ma to dwa skutki, oba dobre: porownanie gustow to
 * porownanie identyfikatorow (a nie tekstow), a gatunki pobieramy z sieci
 * <i>raz na artyste</i> - druga osoba, ktora polubi tego samego wykonawce,
 * nie kosztuje juz ani jednego zapytania na zewnatrz.</p>
 *
 * <p>Realizuje wymaganie nr 7 - kolejna relacja {@code @ManyToMany}
 * (patrz {@code User.favoriteArtists}).</p>
 */
@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identyfikator z Deezera. {@code unique = true} zaklada w bazie indeks
     * unikalny - to on gwarantuje, ze jeden wykonawca = jeden wiersz.
     */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Zdjecie z Deezera. Moze go nie byc - wtedy pokazujemy zastepczy kolor. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Gatunki - do przyblizania dopasowan miedzy ludzmi, ktorzy nie maja
     * wspolnego ani jednego wykonawcy.
     *
     * <p>{@code @ElementCollection} zaklada osobna tabele {@code artist_genres}
     * z kolumnami {@code artist_id} i {@code genre}. To NIE jest encja - gatunek
     * nie ma wlasnego zycia ani identyfikatora, jest tylko etykieta artysty.</p>
     *
     * <p>Zbior bywa <b>pusty</b> i to normalny stan: gatunki pochodza
     * z Last.fm, a klucz do Last.fm jest opcjonalny. Bez niego dopasowanie
     * dziala dalej, tylko opiera sie na wspolnych artystach i znajomych.</p>
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

    /**
     * Rownosc po {@code externalId}, a nie po {@code id}.
     *
     * <p>Artysta jest wkladany do {@link Set} (ulubione uzytkownika) juz
     * w momencie, gdy dopiero powstaje i nie ma jeszcze nadanego {@code id}.
     * Porownywanie po {@code id} dawaloby wtedy {@code null == null} dla dwoch
     * ROZNYCH wykonawcow - i drugi z nich cicho znikalby ze zbioru.</p>
     */
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
