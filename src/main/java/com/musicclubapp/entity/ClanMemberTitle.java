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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Tytul, ktory ma ten czlonek: nadany przez zarzad albo wziety samodzielnie. Tytuly
 * przyznawane automatycznie nie maja tu wiersza - wynikaja z aktywnosci i sa liczone na biezaco.
 */
@Entity
@Table(name = "clan_member_titles",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_member_titles_pair", columnNames = {"title_id", "user_id"}),
    indexes = @Index(name = "idx_clan_member_titles_user", columnList = "user_id"))
public class ClanMemberTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanTitle title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Czlonek wzial go sam (a nie dostal od zarzadu). */
    @Column(name = "self_claimed", nullable = false)
    private boolean selfClaimed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanMemberTitle() {
        // wymagany przez JPA
    }

    public ClanMemberTitle(ClanTitle title, User user, boolean selfClaimed, LocalDateTime now) {
        this.title = title;
        this.user = user;
        this.selfClaimed = selfClaimed;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public ClanTitle getTitle() {
        return title;
    }

    public User getUser() {
        return user;
    }

    public boolean isSelfClaimed() {
        return selfClaimed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
