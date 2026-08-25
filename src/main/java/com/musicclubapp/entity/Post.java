package com.musicclubapp.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Post uzytkownika: tekst, zdjecia i opcjonalnie utwor ze Spotify.
 *
 * <p><b>Realizuje czerwone wymaganie nr 6</b> - relacje OneToMany oraz
 * ManyToOne miedzy dwoma encjami:</p>
 * <ul>
 *   <li>{@code Post} N—1 {@code User} ({@link #author}) - wielu postow
 *       nalezy do jednego uzytkownika,</li>
 *   <li>{@code Post} 1—N {@code PostImage} ({@link #images}) - jeden post
 *       moze miec wiele zdjec.</li>
 * </ul>
 *
 * <p>Wymaganie nr 4 (data i czas) realizuje {@link #createdAt} - po nim
 * sortujemy tablice, zeby najnowsze posty byly na gorze.</p>
 */
@Entity
@Table(name = "posts")
public class Post {

    /** Gorny limit dlugosci tresci - tyle samo pilnuje walidacja w DTO. */
    public static final int MAX_DLUGOSC_TRESCI = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Autor posta - strona ManyToOne relacji.
     *
     * <p>{@code FetchType.LAZY} sprawia, ze uzytkownik jest doczytywany
     * dopiero, gdy faktycznie go potrzebujemy. Przy domyslnym EAGER kazde
     * pobranie posta ciagneloby autora osobnym zapytaniem - przy stu postach
     * daje to sto dodatkowych zapytan (tzw. problem N+1).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Tresc posta. {@code TEXT} zamiast VARCHAR - dluzsze wpisy sie zmieszcza. */
    @Column(nullable = false, length = MAX_DLUGOSC_TRESCI, columnDefinition = "TEXT")
    private String content;

    /**
     * Identyfikator utworu ze Spotify (sam kod, bez calego adresu), np.
     * {@code 4cOdK2wGLETKBW3PvgPWqT}. Trzymamy sam kod, bo z niego skladamy
     * adres odtwarzacza - i nie zapisujemy w bazie tego, co uzytkownik wklei
     * razem z parametrami sledzacymi.
     */
    @Column(name = "spotify_track_id", length = 64)
    private String spotifyTrackId;

    /** Sekunda, od ktorej ma zaczac sie utwor. {@code null} = od poczatku. */
    @Column(name = "spotify_start_seconds")
    private Integer spotifyStartSeconds;

    /**
     * Zdjecia posta - strona OneToMany.
     *
     * <p>{@code cascade = ALL} i {@code orphanRemoval} sprawiaja, ze usuniecie
     * posta kasuje tez wpisy o jego zdjeciach. Bez tego zostawalyby w bazie
     * wiersze wskazujace na nieistniejacy post.</p>
     *
     * <p>{@code @OrderBy} pilnuje, ze zdjecia wracaja z bazy w tej kolejnosci,
     * w ktorej uzytkownik je wgral - inaczej baza moglaby je zwrocic dowolnie.</p>
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PostImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    protected Post() {
    }

    public Post(User author, String content) {
        this.author = author;
        this.content = content;
    }

    /**
     * Dodaje zdjecie i od razu ustawia obie strony relacji.
     *
     * <p>To wazne: gdyby ustawic tylko liste, a nie pole {@code post}
     * w {@link PostImage}, Hibernate zapisalby wiersz z pustym kluczem obcym.
     * Taka metoda pomocnicza to standardowy sposob, zeby o tym nie zapominac.</p>
     */
    public void addImage(PostImage image) {
        image.setPost(this);
        image.setPosition(images.size());
        images.add(image);
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSpotifyTrackId() {
        return spotifyTrackId;
    }

    public void setSpotifyTrackId(String spotifyTrackId) {
        this.spotifyTrackId = spotifyTrackId;
    }

    public Integer getSpotifyStartSeconds() {
        return spotifyStartSeconds;
    }

    public void setSpotifyStartSeconds(Integer spotifyStartSeconds) {
        this.spotifyStartSeconds = spotifyStartSeconds;
    }

    public List<PostImage> getImages() {
        return images;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Post other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
