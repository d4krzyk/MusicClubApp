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
 * Jedno urzadzenie (przegladarka), ktore chce dostawac powiadomienia push.
 *
 * Adres (endpoint) nadaje usluga push przegladarki; p256dh i auth to klucz
 * i sekret, ktorymi szyfrujemy tresc - usluga push jej nie przeczyta.
 */
@Entity
@Table(
    name = "push_subscriptions",
    uniqueConstraints = @UniqueConstraint(name = "uk_push_subscriptions_endpoint", columnNames = "endpoint"),
    indexes = @Index(name = "idx_push_subscriptions_user", columnList = "user_id"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 100)
    private String p256dh;

    @Column(nullable = false, length = 40)
    private String auth;

    /** Jezyk tresci powiadomien na tym urzadzeniu ("pl", "en"). */
    @Column(nullable = false, length = 8)
    private String lang;

    /**
     * Znacznik bezpieczenstwa konta z chwili zapisu. Zmiana hasla, reset albo
     * "wyloguj z innych urzadzen" zmieniaja znacznik konta - i od tej chwili
     * stare urzadzenia nie dostaja nic (ukradziony telefon tez nie). Biezace
     * urzadzenie zapisuje sie ponownie samo przy nastepnym otwarciu aplikacji.
     */
    @Column(nullable = false, length = 32)
    private String stamp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PushSubscription() {
        // wymagany przez JPA
    }

    public PushSubscription(User user, String endpoint, String p256dh, String auth, String lang, LocalDateTime now) {
        this.endpoint = endpoint;
        this.createdAt = now;
        update(user, p256dh, auth, lang);
    }

    public String getStamp() {
        return stamp;
    }

    /**
     * Ten sam adres zapisany ponownie: po zalogowaniu na inne konto na tym
     * samym telefonie urzadzenie przechodzi do nowej osoby.
     */
    public void update(User user, String p256dh, String auth, String lang) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
        this.lang = lang;
        // Konta sprzed znacznikow maja w bazie NULL, dopoki pierwszy raz nie zmienia hasla
        this.stamp = user.getSecurityStamp() == null ? "" : user.getSecurityStamp();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public String getLang() {
        return lang;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
