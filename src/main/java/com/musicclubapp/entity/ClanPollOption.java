package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Jedna z odpowiedzi w ankiecie. */
@Entity
@Table(name = "clan_poll_options")
public class ClanPollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClanPoll poll;

    @Column(nullable = false, length = ClanPoll.OPTION_MAX)
    private String text;

    @Column(nullable = false)
    private int position;

    protected ClanPollOption() {
        // wymagany przez JPA
    }

    ClanPollOption(ClanPoll poll, String text, int position) {
        this.poll = poll;
        this.text = text;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public ClanPoll getPoll() {
        return poll;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }
}
