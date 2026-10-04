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
 * Decyzja jednej osoby o drugiej w trybie Poznawaj. "Tak" jest tajne - druga osoba dowiaduje sie o nim
 * dopiero wtedy, gdy sama tez powie "tak" (wtedy obie zostaja znajomymi, a oba wiersze znikaja). Jedna
 * decyzja na pare (swiper, target); "nie" wygasa i osoba wraca do talii.
 */
@Entity
@Table(name = "discover_swipes",
    uniqueConstraints = @UniqueConstraint(name = "uq_discover_swipes", columnNames = {"swiper_id", "target_id"}),
    indexes = {
        @Index(name = "idx_discover_swipes_target", columnList = "target_id, decision"),
        @Index(name = "idx_discover_swipes_swiper", columnList = "swiper_id, created_at")
    })
public class DiscoverSwipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "swiper_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User swiper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SwipeDecision decision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DiscoverSwipe() {
        // wymagany przez JPA
    }

    public DiscoverSwipe(User swiper, User target, SwipeDecision decision, LocalDateTime createdAt) {
        this.swiper = swiper;
        this.target = target;
        this.decision = decision;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getSwiper() {
        return swiper;
    }

    public User getTarget() {
        return target;
    }

    public SwipeDecision getDecision() {
        return decision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** Ponowna decyzja o tej samej osobie (po wygasnieciu "nie") - nowa tresc i nowa data. */
    public void decide(SwipeDecision decision, LocalDateTime when) {
        this.decision = decision;
        this.createdAt = when;
    }
}
