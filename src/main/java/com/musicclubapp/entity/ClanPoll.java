package com.musicclubapp.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ankieta w klanie: pytanie, kilka odpowiedzi, jeden glos na osobe, termin zamkniecia. */
@Entity
@Table(name = "clan_polls", indexes = @Index(name = "idx_clan_polls_clan", columnList = "clan_id, id"))
public class ClanPoll {

    public static final int QUESTION_MAX = 150;
    public static final int OPTION_MAX = 80;
    public static final int OPTIONS_MIN = 2;
    public static final int OPTIONS_MAX = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = QUESTION_MAX)
    private String question;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ClanPollOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "closes_at", nullable = false)
    private LocalDateTime closesAt;

    /** Zamknieta wczesniej przez autora albo zarzad. */
    @Column(nullable = false)
    private boolean closed;

    protected ClanPoll() {
        // wymagany przez JPA
    }

    public ClanPoll(Clan clan, User author, String question, LocalDateTime now, LocalDateTime closesAt) {
        this.clan = clan;
        this.author = author;
        this.question = question;
        this.createdAt = now;
        this.closesAt = closesAt;
    }

    public void addOption(String text) {
        options.add(new ClanPollOption(this, text, options.size()));
    }

    public boolean isOpen(LocalDateTime now) {
        return !closed && closesAt.isAfter(now);
    }

    public void close() {
        this.closed = true;
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public User getAuthor() {
        return author;
    }

    public String getQuestion() {
        return question;
    }

    public List<ClanPollOption> getOptions() {
        return options;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClosesAt() {
        return closesAt;
    }

    public boolean isClosed() {
        return closed;
    }
}
