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

/** Glos czlonka na propozycje utworu tygodnia. Jeden glos na osobe i propozycje; znika razem z nia. */
@Entity
@Table(name = "clan_track_votes",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_track_votes_pair", columnNames = {"track_id", "user_id"}),
    indexes = @Index(name = "idx_clan_track_votes_user", columnList = "user_id"))
public class ClanTrackVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanTrack track;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanTrackVote() {
        // wymagany przez JPA
    }

    public ClanTrackVote(ClanTrack track, User user, LocalDateTime now) {
        this.track = track;
        this.user = user;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public ClanTrack getTrack() {
        return track;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
