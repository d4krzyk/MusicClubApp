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
