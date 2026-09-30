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

/** Zaproszenie do klanu - jedyna droga do niego. Jedno na pare (klan, zaproszony). */
@Entity
@Table(name = "clan_invitations",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_invitations_pair", columnNames = {"clan_id", "invitee_id"}),
    indexes = @Index(name = "idx_clan_invitations_invitee", columnList = "invitee_id"))
public class ClanInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    protected ClanInvitation() {
        // wymagany przez JPA
    }

    public ClanInvitation(Clan clan, User invitee, User inviter, LocalDateTime now) {
        this.clan = clan;
        this.invitee = invitee;
        this.inviter = inviter;
        this.createdAt = now;
    }

    /** Ponowne zaproszenie po odmowie - wraca jako oczekujace, od kogos (byc moze innego). */
    public void reinvite(User inviter, LocalDateTime now) {
        this.inviter = inviter;
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

    public User getInvitee() {
        return invitee;
    }

    public User getInviter() {
        return inviter;
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
