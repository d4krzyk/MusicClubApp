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
 * Prosba o dolaczenie do klanu. Klan nadal decyduje sam: prosbe przyjmuje albo odrzuca zarzad.
 * Jedna na pare (klan, proszacy); odrzucona zostaje na tydzien, zeby nikt nie naciskal.
 */
@Entity
@Table(name = "clan_join_requests",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_join_requests_pair", columnNames = {"clan_id", "user_id"}),
    indexes = @Index(name = "idx_clan_join_requests_user", columnList = "user_id"))
public class ClanJoinRequest {

    public static final int MESSAGE_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Co proszacy napisal od siebie (niewymagane). */
    @Column(length = MESSAGE_MAX)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    protected ClanJoinRequest() {
        // wymagany przez JPA
    }

    public ClanJoinRequest(Clan clan, User user, String message, LocalDateTime now) {
        this.clan = clan;
        this.user = user;
        this.message = message;
        this.createdAt = now;
    }

    /** Nowa prosba po odmowie (minal tydzien) - wraca jako oczekujaca. */
    public void renew(String message, LocalDateTime now) {
        this.message = message;
        this.status = InvitationStatus.PENDING;
        this.createdAt = now;
        this.answeredAt = null;
    }

    public void decline(LocalDateTime now) {
        this.status = InvitationStatus.DECLINED;
        this.answeredAt = now;
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

    public String getMessage() {
        return message;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}
