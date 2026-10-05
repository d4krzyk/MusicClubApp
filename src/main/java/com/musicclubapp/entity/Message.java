package com.musicclubapp.entity;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.Objects;

/** Jedna wiadomosc wyslana miedzy dwiema osobami. */
@Entity
@Table(
    name = "messages",
    indexes = {
        /* Historia jednej rozmowy to zawsze pytanie "wiadomosci miedzy A i B, od najnowszej". */
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

    /** Tresc wiadomosci. */
    @Column(length = MAX_CONTENT_LENGTH, columnDefinition = "TEXT")
    private String content;

    /* Zalacznik muzyczny - te same szesc kolumn co w encji Post. */

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

    /** GIF z przegladarki GIF-ow - albo {@code null}. */
    @Embedded
    private GifAttachment gif;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Kiedy odbiorca ja przeczytal; null znaczy "jeszcze nie". */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /*
     * Usuniecie rozmowy dziala TYLKO u tego, kto je zlecil - stad dwie flagi
     * zamiast jednej. Wiadomosc zostaje w bazie, dopoki widzi ja druga strona;
     * skasowanie jej naprawde odbieraloby komus jego wlasna korespondencje.
     *
     * "default false" w definicji kolumny jest tu istotne: kolumna dochodzi do
     * tabeli, w ktorej sa juz wiersze, a bez wartosci domyslnej baza odmowilaby
     * dodania jej jako NOT NULL.
     */
    @Column(name = "hidden_for_sender", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean hiddenForSender;

    @Column(name = "hidden_for_recipient", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean hiddenForRecipient;

    /**
     * Kiedy nadawca usunal wiadomosc - u obu stron. Tresc i zalaczniki znikaja od razu, a wiersz zostaje jako slad
     * "wiadomosc usunieta" (inaczej druga strona widzialaby dziure w rozmowie i nie wiedziala, co sie stalo).
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Spotkanie, ktore niesie ta wiadomosc (miejsce, czas, przypomnienie) - albo {@code null}. Usuniecie wiadomosci
     * kasuje spotkanie; skasowanie spotkania zostawia wiadomosc bez niego.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Meeting meeting;

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

    /** Podpina nagranie - albo je usuwa, gdy link jest pusty. */
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

    public GifAttachment getGif() {
        return gif;
    }

    public void attachGif(GifAttachment gif) {
        this.gif = gif;
    }

    /** Czy wiadomosc ma podpiete jakiekolwiek nagranie. */
    public boolean hasMusic() {
        return musicProvider != null && musicExternalId != null;
    }

    /** Usuwa tresc i zalaczniki u obu stron; zostaje slad z data. Drugie usuniecie niczego nie zmienia. */
    public void deleteForEveryone(LocalDateTime now) {
        if (deletedAt != null) {
            return;
        }
        content = null;
        applyMusic(null, null, null, null);
        gif = null;
        meeting = null;
        deletedAt = now;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public void attachMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /** Oznacza jako przeczytana. */
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

    public boolean isHiddenForSender() {
        return hiddenForSender;
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
