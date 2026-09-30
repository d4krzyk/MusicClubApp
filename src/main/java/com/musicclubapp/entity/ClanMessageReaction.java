package com.musicclubapp.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Reakcja czlonka na wiadomosc z czatu klanu. Jedna osoba - jedna reakcja na wiadomosc
 * (zmiana emoji podmienia poprzednia). Znika razem z wiadomoscia (ON DELETE CASCADE).
 */
@Entity
@Table(name = "clan_message_reactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_message_reactions_pair", columnNames = {"message_id", "user_id"}),
    indexes = @Index(name = "idx_clan_message_reactions_user", columnList = "user_id"))
public class ClanMessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanEmoji type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanMessageReaction() {
        // wymagany przez JPA
    }

    public ClanMessageReaction(ClanMessage message, User user, ClanEmoji type, LocalDateTime now) {
        this.message = message;
        this.user = user;
        this.type = type;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public ClanMessage getMessage() {
        return message;
    }

    public User getUser() {
        return user;
    }

    public ClanEmoji getType() {
        return type;
    }

    public void setType(ClanEmoji type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
