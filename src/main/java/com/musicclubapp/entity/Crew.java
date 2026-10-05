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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Ekipa na koncert: kilka osob, ktore ida na to samo wydarzenie razem - skad jada, ilu ich moze byc, jak sie wchodzi.
 * Wydarzenie z ekipa import wycofuje zamiast kasowac (jak przy zapisach); znika razem z nim 30 dni po dacie.
 */
@Entity
@Table(name = "crews", indexes = @Index(name = "idx_crews_event", columnList = "event_id"))
public class Crew {

    public static final int MAX_TITLE = 60;
    public static final int MAX_DESCRIPTION = 300;
    public static final int MAX_CITY = 60;
    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 12;
    public static final int DEFAULT_CAPACITY = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MusicEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "founder_id", nullable = false)
    private User founder;

    @Column(length = MAX_TITLE)
    private String title;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 16)
    private CrewJoinPolicy joinPolicy;

    /** Skad ekipa wyrusza (miasto z listy, jak w profilu) - po nim stawiamy wyzej ekipy z okolicy. */
    @Column(name = "departure_city", length = MAX_CITY)
    private String departureCity;

    @Column(name = "departure_city_key", length = 100)
    private String departureCityKey;

    @Column(name = "departure_lat")
    private Double departureLat;

    @Column(name = "departure_lon")
    private Double departureLon;

    /** Zakladajacy zamknal nabor: nikt nowy nie dolaczy ani nie poprosi. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean closed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Crew() {
        // wymagany przez JPA
    }

    public Crew(MusicEvent event, User founder, LocalDateTime now) {
        this.event = event;
        this.founder = founder;
        this.capacity = DEFAULT_CAPACITY;
        this.joinPolicy = CrewJoinPolicy.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void describe(String title, String description, int capacity, CrewJoinPolicy joinPolicy, LocalDateTime now) {
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.joinPolicy = joinPolicy;
        this.updatedAt = now;
    }

    public void departFrom(String city, String cityKey, Double lat, Double lon) {
        this.departureCity = city;
        this.departureCityKey = cityKey;
        this.departureLat = lat;
        this.departureLon = lon;
    }

    public void setClosed(boolean closed, LocalDateTime now) {
        this.closed = closed;
        this.updatedAt = now;
    }

    public void handOver(User founder, LocalDateTime now) {
        this.founder = founder;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public MusicEvent getEvent() {
        return event;
    }

    public User getFounder() {
        return founder;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public CrewJoinPolicy getJoinPolicy() {
        return joinPolicy;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public String getDepartureCityKey() {
        return departureCityKey;
    }

    public Double getDepartureLat() {
        return departureLat;
    }

    public Double getDepartureLon() {
        return departureLon;
    }

    public boolean isClosed() {
        return closed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Crew c && id != null && id.equals(c.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
