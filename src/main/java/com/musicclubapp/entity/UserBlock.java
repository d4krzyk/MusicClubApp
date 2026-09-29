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

import java.time.LocalDateTime;

/**
 * Jedna osoba zablokowala druga.
 *
 * <p>Blokada dziala w obie strony: zadna z nich nie widzi postow ani
 * profilu drugiej, nie moze jej zaprosic, a znajomosc znika. Zablokowany
 * nie dostaje o tym zadnej informacji - profil blokujacego wyglada dla niego
 * tak, jakby konta nie bylo.</p>
 */
@Entity
@Table(name = "user_blocks",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"}),
    indexes = @Index(name = "idx_user_blocks_blocked", columnList = "blocked_id"))
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserBlock() {
    }

    public UserBlock(User blocker, User blocked, LocalDateTime createdAt) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = createdAt;
    }

    public User getBlocked() {
        return blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
