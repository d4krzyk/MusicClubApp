package com.musicclubapp.entity;

import jakarta.persistence.Column;
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

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanMessage() {
        // wymagany przez JPA
    }

    public ClanMessage(Clan clan, User sender, String content) {
        this.clan = clan;
        this.sender = sender;
        this.content = content;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
