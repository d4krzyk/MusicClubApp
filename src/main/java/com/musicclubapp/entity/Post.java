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
 * Post uzytkownika: tekst, zdjecia i opcjonalnie nagranie z serwisu
 * muzycznego (Spotify albo YouTube).
 *
 * <p><b>Realizuje czerwone wymaganie nr 6</b> - relacje OneToMany oraz
 * ManyToOne miedzy dwoma encjami:</p>
 * <ul>
 *   <li>{@code Post} N—1 {@code User} ({@link #author}) - wielu postow
 *       nalezy do jednego uzytkownika,</li>
 *   <li>{@code Post} 1—N {@code PostImage} ({@link #images}) - jeden post
 *       moze miec wiele zdjec,</li>
 *   <li>{@code Post} 1—N {@code Reaction} ({@link #reactions}) - a takze
 *       wiele reakcji, kazda od innego uzytkownika.</li>
 * </ul>
 *
 * <p>Wymaganie nr 4 (data i czas) realizuje {@link #createdAt} - po nim
 * sortujemy tablice, zeby najnowsze posty byly na gorze.</p>
 */
@Entity
@Table(name = "posts")
public class Post {

    /** Gorny limit dlugosci tresci - tyle samo pilnuje walidacja w DTO. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /**
     * Najpozniejszy moment startu utworu, jaki przyjmujemy - 30 minut.
     *
     * <p><b>Dlaczego nie sprawdzamy prawdziwej dlugosci nagrania?</b> Zeby ja
     * poznac, trzeba pelnego API serwisu - a Spotify ogranicza je do pieciu
     * kont w trybie deweloperskim. Publiczny oEmbed, z ktorego korzystamy,
     * oddaje tytul i miniaturke, ale nie czas trwania. Do tego czasu
     * pilnujemy zakresu, ktory ma sens dla utworu muzycznego.</p>
     */
    public static final int MAX_SEKUNDA_STARTU = 1800;

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
    @Column(nullable = false, length = MAX_CONTENT_LENGTH, columnDefinition = "TEXT")
    private String content;

    /**
     * Serwis, z ktorego pochodzi link - albo {@code null}, gdy post jest
     * bez muzyki.
     *
     * <p>{@code EnumType.STRING} zapisuje w bazie napis, a nie pozycje
     * na liscie - patrz komentarz przy {@code Reaction.type}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "music_provider", length = 16)
    private MusicProvider musicProvider;

    /** Utwor, album czy artysta. */
    @Enumerated(EnumType.STRING)
    @Column(name = "music_kind", length = 16)
    private MusicKind musicKind;

    /**
     * Identyfikator nagrania w serwisie - sam kod, bez calego adresu.
     *
     * <p>Trzymamy sam kod z dwoch powodow: nie zapisujemy parametrow
     * sledzacych, ktore serwisy dokleja do udostepnianych linkow, a poza tym
     * ten sam utwor ma zawsze ten sam identyfikator - bez tego liczenie
     * "najczesciej wrzucanych" rozjezdzaloby sie przy kazdej innej postaci
     * adresu.</p>
     */
    @Column(name = "music_external_id", length = 300)
    private String musicExternalId;

    /**
     * Tytul i miniaturka pobrane RAZ, przy dodawaniu posta (oEmbed).
     *
     * <p>Zapisujemy je u siebie, zamiast odpytywac serwis przy kazdym
     * wyswietleniu tablicy. Moga byc puste - gdy serwis akurat nie odpowie,
     * post i tak powstaje, tylko bez tytulu. Odtwarzacz dziala niezaleznie
     * od tego, bo {@code <iframe>} pobiera sobie wszystko sam.</p>
     */
    @Column(name = "music_title", length = 300)
    private String musicTitle;

    @Column(name = "music_thumbnail_url", length = 500)
    private String musicThumbnailUrl;

    /**
     * Sekunda, od ktorej ma zaczac sie utwor. {@code null} = od poczatku.
     *
     * <p>Ma sens WYLACZNIE przy {@link MusicKind#TRACK} - pilnuje tego
     * walidator {@code ValidMusicLink}.</p>
     */
    @Column(name = "music_start_seconds")
    private Integer musicStartSeconds;

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

    /**
     * Reakcje na post - druga relacja OneToMany tej encji.
     *
     * <p><b>Po co ta lista, skoro liczniki i tak liczymy zapytaniem?</b>
     * Dla {@code cascade} przy usuwaniu. Bez niej skasowanie posta, na ktory
     * ktos zareagowal, konczy sie bledem klucza obcego - w tabeli reakcji
     * zostalyby wiersze wskazujace na nieistniejacy post.</p>
     *
     * <p>Do wyswietlania licznikow tej kolekcji <b>nie uzywamy</b>: przy
     * dwudziestu postach na stronie Hibernate poszedlby po reakcje kazdego
     * z nich osobno (problem N+1). Zamiast tego jedno zapytanie grupujace
     * liczy wszystko naraz - patrz {@code ReactionRepository}.</p>
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reaction> reactions = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Kto moze ten post zobaczyc - patrz {@link PostVisibility}.
     *
     * <p><b>Kolumna jest w bazie NULLOWALNA, choc w Javie nigdy nie jest pusta</b>,
     * i to jest swiadoma decyzja. Projekt chodzi na {@code ddl-auto=update}:
     * Hibernate dokladajac kolumne oznaczona {@code nullable = false} do tabeli,
     * w ktorej sa juz wiersze, dostaje od bazy odmowe - kolumna wtedy w ogole
     * nie powstaje, a kazde zapytanie o posty konczy sie bledem. Dlatego
     * kolumna wchodzi jako nullowalna, a stare wiersze uzupelnia
     * {@code PostVisibilityMigration} przy pierwszym starcie.</p>
     *
     * <p>Domyslna wartosc jest ustawiona przy polu, wiec kazdy <b>nowy</b> post
     * ma ja od razu - takze taki, ktory powstaje w tescie przez konstruktor.</p>
     */
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

    /**
     * Ustawia muzyke posta - albo ja calkowicie usuwa, gdy {@code link}
     * jest pusty.
     *
     * <p>Jedna metoda na wszystkie pola naraz, zeby nie dalo sie zostawic
     * posta w polowicznym stanie (np. z identyfikatorem, ale bez serwisu).
     * Wyczyszczenie linku kasuje takze tytul, miniaturke i moment startu -
     * bez nagrania nie maja do czego sie odnosic.</p>
     */
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

    /** Nigdy nie zwraca {@code null} - patrz komentarz przy polu. */
    public PostVisibility getVisibility() {
        return visibility == null ? PostVisibility.PUBLIC : visibility;
    }

    /** Pusta wartosc znaczy "zostaw jak jest" - post nie moze byc bez widocznosci. */
    public void setVisibility(PostVisibility visibility) {
        if (visibility != null) {
            this.visibility = visibility;
        }
    }

    /**
     * Czy dana osoba ma prawo zobaczyc ten post.
     *
     * <p><b>Ta sama regula jest zapisana dwa razy</b> - tutaj i w JPQL-u
     * {@code PostRepository.findFeed}. Nie da sie inaczej: pojedynczy post
     * sprawdzamy w Javie, a cala tablice musi odsiac baza, bo inaczej trzeba by
     * sciagnac wszystkie posty i odrzucic wiekszosc juz po stronie aplikacji -
     * razem ze stronicowaniem liczacym wtedy zle. Zgodnosc obu zapisow pilnuje
     * test {@code PostVisibilityTest#regulaWJavieIWBazieDajaToSamo}.</p>
     *
     * @param viewer zalogowany uzytkownik; {@code null} = nikt niezalogowany
     */
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
