package com.musicclubapp.entity;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Jedna wiadomosc wyslana miedzy dwiema osobami.
 *
 * <p><b>Nie ma tu encji "rozmowa"</b> - i to jest decyzja, a nie
 * przeoczenie. Rozmowa dwoch osob nie ma zadnego wlasnego stanu:
 * wszystko, co o niej wiemy (kto z kim, kiedy ostatnio, ile
 * nieprzeczytanych), da sie policzyc z samych wiadomosci. Osobna tabela
 * bylaby wiec druga kopia tej samej prawdy - a dwie kopie predzej czy
 * pozniej sie rozjezdzaja. Tutaj rozjechac sie nie ma czemu.</p>
 *
 * <p>Cena jest jedna: liste rozmow trzeba <b>wyliczyc</b> zapytaniem
 * grupujacym zamiast odczytac wprost. Robi to {@code MessageRepository}
 * dwoma zapytaniami na cale okno czatu - patrz komentarze tam.</p>
 *
 * <p><b>Kolejne relacje {@code ManyToOne}</b> (wymaganie nr 6): nadawca
 * i odbiorca, oboje wskazujacy na te sama tabele {@code users}.</p>
 */
@Entity
@Table(
    name = "messages",
    indexes = {
        /*
         * Historia jednej rozmowy to zawsze pytanie "wiadomosci miedzy A i B,
         * od najnowszej". Baza szuka wtedy po nadawcy albo po odbiorcy, wiec
         * potrzebne sa OBA indeksy - jeden nie zastapi drugiego, bo zapytanie
         * laczy warunki przez OR.
         */
        @Index(name = "idx_messages_sender", columnList = "sender_id, created_at"),
        @Index(name = "idx_messages_recipient", columnList = "recipient_id, created_at")
    })
public class Message {

    /** Gorny limit dlugosci - tyle samo pilnuje walidacja w DTO. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * Tresc wiadomosci. <b>Moze byc pusta</b> - gdy ktos wysyla sam link
     * do utworu, dopisywanie do niego tekstu byloby udawaniem, ze cos
     * napisal.
     *
     * <p>Pilnujemy tylko tego, zeby wiadomosc nie byla pusta CALA -
     * bez tresci i bez muzyki nie ma czego wyslac. Sprawdza to
     * {@code MessageHasContentValidator}.</p>
     */
    @Column(length = MAX_CONTENT_LENGTH, columnDefinition = "TEXT")
    private String content;

    /*
     * ------------------------------------------------------------------
     *  Zalacznik muzyczny - te same szesc kolumn co w encji Post.
     * ------------------------------------------------------------------
     *
     *  DLACZEGO NIE WSPOLNY @Embeddable DLA OBU ENCJI. Kusi, zeby wyciagnac
     *  to do jednej klasy osadzonej i uzyc jej w Poscie i tutaj. Dwa powody,
     *  dla ktorych tego nie robimy:
     *
     *  1. Tabela "posts" ISTNIEJE i ma dane. Przepisanie jej mapowania to
     *     zmiana w dzialajacym, przetestowanym kodzie w zamian za oszczednosc
     *     szesciu deklaracji pol - zla proporcja ryzyka do zysku.
     *
     *  2. Hibernate zwraca dla klasy osadzonej NULL, gdy wszystkie jej
     *     kolumny sa puste. Post bez muzyki (czyli wiekszosc postow) dawalby
     *     wiec puste odwolanie zamiast pustego obiektu, a kazdy odczyt
     *     wymagalby sprawdzenia. To pulapka, ktora odzywa sie dopiero
     *     w dzialajacej aplikacji.
     *
     *  Powtarzaja sie tu wylacznie DEKLARACJE KOLUMN. Cala logika muzyki -
     *  rozpoznawanie linku (MusicLinkParser), skladanie adresow (MusicEmbed),
     *  pobieranie tytulu (MusicMetadataService) i walidacja (@ValidMusicLink) -
     *  jest jedna i wspolna. To ona bylaby kosztowna w duplikacji.
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "music_provider", length = 16)
    private MusicProvider musicProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "music_kind", length = 16)
    private MusicKind musicKind;

    @Column(name = "music_external_id", length = 300)
    private String musicExternalId;

    @Column(name = "music_title", length = 300)
    private String musicTitle;

    @Column(name = "music_thumbnail_url", length = 500)
    private String musicThumbnailUrl;

    @Column(name = "music_start_seconds")
    private Integer musicStartSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Kiedy odbiorca ja przeczytal; {@code null} znaczy "jeszcze nie".
     *
     * <p>Data zamiast flagi - tak samo jak przy powiadomieniach. Kosztuje
     * tyle samo, a pozwala kiedys pokazac "przeczytano o 14:32".</p>
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Message() {
    }

    public Message(User sender, User recipient, String content) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Podpina nagranie - albo je usuwa, gdy {@code link} jest pusty.
     *
     * <p>Jedna metoda na wszystkie pola naraz, dokladnie jak
     * {@code Post.applyMusic}: inaczej dalo by sie zostawic wiadomosc
     * w polowicznym stanie, np. z identyfikatorem, ale bez serwisu.</p>
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
        // Moment startu ma sens tylko przy pojedynczym utworze
        this.musicStartSeconds = link.kind().supportsStartSeconds() ? startSeconds : null;
    }

    /** Czy wiadomosc ma podpiete jakiekolwiek nagranie. */
    public boolean hasMusic() {
        return musicProvider != null && musicExternalId != null;
    }

    /**
     * Oznacza jako przeczytana. Powtorne wywolanie nic nie zmienia -
     * pierwsza chwila przeczytania jest ta prawdziwa.
     */
    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
