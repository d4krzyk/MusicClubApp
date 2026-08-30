package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

/** Jedna linijka dowodu dolaczona do zgloszenia - migawka, nie odnosnik. */
@Embeddable
public class ReportEvidence {

    /** Kto to napisal - sam login, bo tresc ma przetrwac kasowanie konta. */
    @Column(name = "author", nullable = false, length = 50)
    private String author;

    /** Co napisal; przy samym nagraniu - jego tytul. */
    @Column(name = "text", length = 2000, columnDefinition = "TEXT")
    private String text;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected ReportEvidence() {
    }

    public ReportEvidence(String author, String text, LocalDateTime sentAt) {
        this.author = author;
        this.text = text;
        this.sentAt = sentAt;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
