package com.musicclubapp.entity;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Post uzytkownika: tekst, zdjecia i opcjonalnie nagranie z serwisu muzycznego (Spotify albo
 * YouTube).
 */
@Entity
@Table(name = "posts")
public class Post {

    /** Gorny limit dlugosci tresci - tyle samo pilnuje walidacja w DTO. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /** Najpozniejszy moment startu utworu, jaki przyjmujemy - 30 minut. */
    public static final int MAX_SEKUNDA_STARTU = 1800;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Autor posta - strona ManyToOne relacji. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Tresc posta. {@code TEXT} zamiast VARCHAR - dluzsze wpisy sie zmieszcza. */
    @Column(nullable = false, length = MAX_CONTENT_LENGTH, columnDefinition = "TEXT")
    private String content;

    /** Serwis, z ktorego pochodzi link - albo null, gdy post jest bez muzyki. */
    @Enumerated(EnumType.STRING)
    @Column(name = "music_provider", length = 16)
    private MusicProvider musicProvider;

    /** Utwor, album czy artysta. */
    @Enumerated(EnumType.STRING)
    @Column(name = "music_kind", length = 16)
    private MusicKind musicKind;

    /** Identyfikator nagrania w serwisie - sam kod, bez calego adresu. */
    @Column(name = "music_external_id", length = 300)
    private String musicExternalId;

    /** Tytul i miniaturka pobrane RAZ, przy dodawaniu posta (oEmbed). */
    @Column(name = "music_title", length = 300)
    private String musicTitle;

    @Column(name = "music_thumbnail_url", length = 500)
    private String musicThumbnailUrl;

    /** Sekunda, od ktorej ma zaczac sie utwor. null = od poczatku. */
    @Column(name = "music_start_seconds")
    private Integer musicStartSeconds;

    /**
     * Zdjecia posta - strona OneToMany. cascade = ALL i orphanRemoval sprawiaja, ze usuniecie
     * posta kasuje tez wpisy o jego zdjeciach.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PostImage> images = new ArrayList<>();

    /** Reakcje na post - druga relacja OneToMany tej encji. */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reaction> reactions = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Kto moze ten post zobaczyc - patrz PostVisibility. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 16)
    private PostVisibility visibility = PostVisibility.PUBLIC;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        // Zabezpieczenie na wypadek encji odtworzonej z pominieciem konstruktora
        if (visibility == null) {
            visibility = PostVisibility.PUBLIC;
        }
    }

    protected Post() {
    }

    public Post(User author, String content) {
        this.author = author;
        this.content = content;
    }

    /** Dodaje zdjecie i od razu ustawia obie strony relacji. */
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

    /** Ustawia muzyke posta - albo ja calkowicie usuwa, gdy link jest pusty. */
    public void applyMusic(ParsedMusicLink link, Integer startSeconds,
                            String title, String thumbnailUrl) {
        if (link == null) {
            this.musicProvider = null;
            this.musicKind = null;
            this.musicExternalId = null;
            this.musicTitle = null;
            this.musicThumbnailUrl = null;
            this.musicStartSeconds = null;
            return;
        }

        this.musicProvider = link.provider();
        this.musicKind = link.kind();
        this.musicExternalId = link.externalId();
        this.musicTitle = title;
        this.musicThumbnailUrl = thumbnailUrl;
        // Moment startu ma sens tylko przy utworze
        this.musicStartSeconds = link.kind().supportsStartSeconds() ? startSeconds : null;
    }

    /** Czy post ma podpiete jakiekolwiek nagranie. */
    public boolean hasMusic() {
        return musicProvider != null && musicExternalId != null;
    }

    public MusicProvider getMusicProvider() {
        return musicProvider;
    }

    public MusicKind getMusicKind() {
        return musicKind;
    }

    public String getMusicExternalId() {
        return musicExternalId;
    }

    public String getMusicTitle() {
        return musicTitle;
    }

    public String getMusicThumbnailUrl() {
        return musicThumbnailUrl;
    }

    public Integer getMusicStartSeconds() {
        return musicStartSeconds;
    }

    public List<PostImage> getImages() {
        return images;
    }

    public List<Reaction> getReactions() {
        return reactions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** Nigdy nie zwraca {@code null}. */
    public PostVisibility getVisibility() {
        return visibility == null ? PostVisibility.PUBLIC : visibility;
    }

    /** Pusta wartosc znaczy "zostaw jak jest" - post nie moze byc bez widocznosci. */
    public void setVisibility(PostVisibility visibility) {
        if (visibility != null) {
            this.visibility = visibility;
        }
    }

    /** Czy dana osoba ma prawo zobaczyc ten post. */
    public boolean isVisibleTo(User viewer) {
        if (getVisibility() == PostVisibility.PUBLIC) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        // Autor zawsze widzi swoje - nawet gdyby nie mial ani jednego znajomego
        return author.getId().equals(viewer.getId())
            || author.getFriends().contains(viewer);
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
