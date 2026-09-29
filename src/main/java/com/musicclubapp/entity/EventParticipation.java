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

/**
 * Czyjs zapis na wydarzenie: zainteresowany albo idzie.
 *
 * Rezygnacja to usuniecie wiersza, a nie trzeci stan - "zrezygnowal" niczym
 * sie nie rozni od "nigdy nie kliknal", a lista uczestnikow i liczniki
 * nie musza wtedy niczego odfiltrowywac.
 */
@Entity
@Table(
    name = "event_participations",
    uniqueConstraints = {
        /* Jedna osoba = jeden zapis na wydarzenie. Pilnuje tego baza, nie tylko kod. */
        @UniqueConstraint(name = "uk_event_participations_event_user", columnNames = {"event_id", "user_id"})
    },
    indexes = {
        /* Zakladka "Moje" i usuwanie konta szukaja po osobie. */
        @Index(name = "idx_event_participations_user", columnList = "user_id")
    })
public class EventParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private MusicEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipationStatus status;

    /**
     * "Nie pokazuj mnie na liscie uczestnikow". Taka osoba dalej liczy sie
     * do licznika - tylko bez nazwiska. Lista mowi przeciez, gdzie ktos
     * bedzie i kiedy, a nie kazdy chce to oglaszac.
     */
    @Column(nullable = false)
    private boolean hidden;

    /**
     * Ostatni prog przypomnienia, ktory ta osoba juz ma za soba (np. 3 = "za
     * 3 dni" wyslane). null = jeszcze zadne. Przy zapisie ustawiany na biezacy
     * prog - kto zapisuje sie dwa dni przed koncertem, wie, kiedy on jest,
     * i dostanie dopiero "jutro".
     */
    @Column(name = "reminded_days")
    private Integer remindedDays;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EventParticipation() {
        // wymagany przez JPA
    }

    public EventParticipation(MusicEvent event, User user, ParticipationStatus status,
                              boolean hidden, LocalDateTime now) {
        this.event = event;
        this.user = user;
        this.status = status;
        this.hidden = hidden;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(ParticipationStatus status, boolean hidden, LocalDateTime now) {
        this.status = status;
        this.hidden = hidden;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public MusicEvent getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public ParticipationStatus getStatus() {
        return status;
    }

    public boolean isHidden() {
        return hidden;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getRemindedDays() {
        return remindedDays;
    }

    public void markReminded(Integer threshold) {
        this.remindedDays = threshold;
    }
}
