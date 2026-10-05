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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Prosba o miejsce w ekipie z naborem "za zgoda". Odrzucona zostaje (z data) - przez tydzien nie da sie poprosic
 * ponownie tej samej ekipy; przyjeta znika (osoba jest juz czlonkiem).
 */
@Entity
@Table(name = "crew_requests",
    uniqueConstraints = @UniqueConstraint(name = "uk_crew_request", columnNames = {"crew_id", "user_id"}))
public class CrewRequest {

    public static final int MAX_MESSAGE = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Crew crew;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(length = MAX_MESSAGE)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Kiedy zakladajacy odmowil; null = czeka. */
    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    protected CrewRequest() {
        // wymagany przez JPA
    }

    public CrewRequest(Crew crew, User user, String message, LocalDateTime now) {
        this.crew = crew;
        this.user = user;
        this.message = message;
        this.createdAt = now;
    }

    public void decline(LocalDateTime now) {
        this.declinedAt = now;
    }

    /** Ponowna prosba po uplywie tygodnia od odmowy - ten sam wiersz zaczyna od nowa. */
    public void renew(String message, LocalDateTime now) {
        this.message = message;
        this.createdAt = now;
        this.declinedAt = null;
    }

    public boolean isPending() {
        return declinedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Crew getCrew() {
        return crew;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeclinedAt() {
        return declinedAt;
    }
}
