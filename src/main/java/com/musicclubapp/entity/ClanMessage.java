package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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

/** Wiadomosc na czacie klanu. Widza ja czlonkowie (i administrator aplikacji). */
@Entity
@Table(name = "clan_messages",
    indexes = @Index(name = "idx_clan_messages_clan", columnList = "clan_id, id"))
public class ClanMessage {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Tresc; pusty napis, gdy wiadomosc to sam GIF (kolumna zostaje NOT NULL - jak w komentarzach). */
    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    /** GIF z przegladarki GIF-ow - albo {@code null}. */
    @Embedded
    private GifAttachment gif;

    /**
     * Wiadomosc, na ktora ta odpowiada, albo null. Skasowanie tamtej wiadomosci nie usuwa tej -
     * baza sama czysci odnosnik (ON DELETE SET NULL), jak przy poscie pod wydarzeniem.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ClanMessage replyTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Nagranie rozpoznane w tresci (link do YouTube, Spotify albo Apple Music) - albo {@code null}. */
    @Embedded
    private MusicAttachment music;

    /**
     * Kiedy wiadomosc usunieto (autor, zarzad klanu albo administrator aplikacji). Tresc i zalaczniki znikaja, a wiersz
     * zostaje jako slad "wiadomosc usunieta" - odpowiedzi na nia nie traca sensu, a pozostali widza, ze cos zniknelo.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ClanMessage() {
        // wymagany przez JPA
    }

    public ClanMessage(Clan clan, User sender, String content) {
        this.clan = clan;
        this.sender = sender;
        this.content = content;
    }

    public ClanMessage(Clan clan, User sender, String content, ClanMessage replyTo) {
        this(clan, sender, content);
        this.replyTo = replyTo;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public ClanMessage getReplyTo() {
        return replyTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public GifAttachment getGif() {
        return gif;
    }

    public void attachGif(GifAttachment gif) {
        this.gif = gif;
    }

    public MusicAttachment getMusic() {
        return music;
    }

    public void attachMusic(MusicAttachment music) {
        this.music = music;
    }

    /** Usuwa tresc i zalaczniki; zostaje slad z data. Drugie usuniecie niczego nie zmienia. */
    public void delete(LocalDateTime now) {
        if (deletedAt != null) {
            return;
        }
        content = "";
        gif = null;
        music = null;
        deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
