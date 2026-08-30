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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Kiedy odbiorca ja przeczytal; null znaczy "jeszcze nie". */
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

    /** Czy wiadomosc ma podpiete jakiekolwiek nagranie. */
    public boolean hasMusic() {
        return musicProvider != null && musicExternalId != null;
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
