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

import java.time.LocalDateTime;

/**
 * Link potwierdzajacy adres e-mail - jeden na kazda wyslana wiadomosc.
 *
 * <p>W bazie lezy SKROT tokenu (SHA-256), a nie sam token. Kto dostanie kopie
 * bazy, nie potwierdzi nia niczyjego adresu - tak jak z haslem nie zaloguje
 * sie nikt, kto zna tylko jego hash.</p>
 *
 * <p>Token potwierdza konkretny adres ({@link #email}), a nie "konto".
 * Gdy ktos w miedzyczasie zmieni adres, stary link przestaje cokolwiek
 * potwierdzac, zamiast potwierdzic nowy adres, ktorego nikt nie sprawdzil.</p>
 */
@Entity
@Table(name = "email_tokens", indexes = {
    @Index(name = "idx_email_tokens_user_created", columnList = "user_id, created_at")
})
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 tokenu zapisany szesnastkowo - zawsze 64 znaki. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Adres, na ktory poszla wiadomosc - i ktory ten link potwierdza. */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected EmailToken() {
    }

    public EmailToken(User user, String tokenHash, String email, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.email = email;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
