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

/** Glos w ankiecie: jeden na osobe (zmiana odpowiedzi podmienia glos). Znika razem z ankieta. */
@Entity
@Table(name = "clan_poll_votes",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_poll_votes_pair", columnNames = {"poll_id", "user_id"}),
    indexes = {
        @Index(name = "idx_clan_poll_votes_user", columnList = "user_id"),
        @Index(name = "idx_clan_poll_votes_option", columnList = "option_id")
    })
public class ClanPollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanPoll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanPollOption option;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanPollVote() {
        // wymagany przez JPA
    }

    public ClanPollVote(ClanPoll poll, ClanPollOption option, User user, LocalDateTime now) {
        this.poll = poll;
        this.option = option;
        this.user = user;
        this.createdAt = now;
    }

    public void change(ClanPollOption option, LocalDateTime now) {
        this.option = option;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public ClanPoll getPoll() {
        return poll;
    }

    public ClanPollOption getOption() {
        return option;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
