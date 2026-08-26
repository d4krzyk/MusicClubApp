package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Zaproszenie do znajomych, ktore czeka na odpowiedz.
 *
 * <p><b>Dlaczego nie ma tu pola "status"?</b> Bo w tabeli trzymamy WYLACZNIE
 * zaproszenia oczekujace. Akceptacja dopisuje znajomosc do
 * {@code user_friends} i kasuje ten wiersz; odrzucenie po prostu go kasuje.
 * Dzieki temu nie trzeba w kazdym zapytaniu pamietac o dopisaniu
 * {@code WHERE status = 'PENDING'} - a to jeden z tych warunkow, ktore
 * najlatwiej przeoczyc i potem dziwic sie, skad na liscie wzieli sie ludzie,
 * ktorzy nas odrzucili.</p>
 *
 * <p>Cena: nie wiemy, kto kogo kiedys odrzucil. Do dzialania aplikacji nie jest
 * to potrzebne, a odrzucona osoba moze sprobowac ponownie - co akurat jest
 * zachowaniem oczekiwanym.</p>
 *
 * <p>Kolejna para {@code ManyToOne} w projekcie: zaproszenie wskazuje
 * na nadawce ORAZ na odbiorce.</p>
 */
@Entity
@Table(
    name = "friend_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_friend_request_para",
        columnNames = {"sender_id", "recipient_id"}))
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kto zaprasza. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Kogo zaprasza. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Wymaganie nr 4 - pokazujemy "wyslano 3 dni temu". */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected FriendRequest() {
    }

    public FriendRequest(User sender, User recipient) {
        this.sender = sender;
        this.recipient = recipient;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendRequest other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
