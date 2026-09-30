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
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * Czlonkostwo w klanie. Jedna osoba to najwyzej jeden klan - pilnuje tego unikalnosc
 * {@code user_id}, wiec dwa rownolegle przyjecia zaproszen nie dadza dwoch wierszy.
 */
@Entity
@Table(name = "clan_members",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_members_user", columnNames = "user_id"),
    indexes = @Index(name = "idx_clan_members_clan", columnList = "clan_id"))
public class ClanMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** Na jaki kolor klanu glosuje ta osoba (albo null). */
    @Enumerated(EnumType.STRING)
    @Column(name = "color_vote", length = 16)
    private ClanColor colorVote;

    /** Kiedy oddala glos - przy remisie wygrywa kolor, na ktory glosowano wczesniej. */
    @Column(name = "color_voted_at")
    private LocalDateTime colorVotedAt;

    /**
     * Numer ostatniej wiadomosci czatu, jaka ta osoba widziala. Wszystko nowsze (od innych osob)
     * to "nieprzeczytane". null = jeszcze nic nie czytala. Nowy czlonek startuje od ostatniej
     * wiadomosci w klanie - historia sprzed jego wejscia nie liczy sie jako nowa.
     */
    @Column(name = "chat_read_id")
    private Long chatReadId;

    /** Wyciszony czat: nadal liczy nieprzeczytane, ale nie wysyla powiadomien na telefon. */
    @ColumnDefault("false")
    @Column(name = "chat_muted", nullable = false)
    private boolean chatMuted;

    protected ClanMember() {
        // wymagany przez JPA
    }

    public ClanMember(Clan clan, User user, ClanRole role, LocalDateTime joinedAt) {
        this.clan = clan;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public void vote(ClanColor color, LocalDateTime now) {
        this.colorVote = color;
        this.colorVotedAt = color == null ? null : now;
    }

    public Long getChatReadId() {
        return chatReadId;
    }

    /** Pamieta tylko ruch do przodu - pozniejszy, spozniony odczyt nie cofa przeczytanych. */
    public void markChatRead(long upTo) {
        if (chatReadId == null || upTo > chatReadId) {
            chatReadId = upTo;
        }
    }

    public boolean isChatMuted() {
        return chatMuted;
    }

    public void setChatMuted(boolean chatMuted) {
        this.chatMuted = chatMuted;
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public User getUser() {
        return user;
    }

    public ClanRole getRole() {
        return role;
    }

    public void setRole(ClanRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public ClanColor getColorVote() {
        return colorVote;
    }

    public LocalDateTime getColorVotedAt() {
        return colorVotedAt;
    }
}
