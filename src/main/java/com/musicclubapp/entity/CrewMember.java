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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Czlonek ekipy. Jedna osoba to najwyzej jedna ekipa na wydarzenie - pilnuje tego unikalnosc (event_id, user_id),
 * stad wydarzenie zapisane tez tutaj (dwa rownolegle dolaczenia nie dadza dwoch wierszy).
 */
@Entity
@Table(name = "crew_members",
    uniqueConstraints = @UniqueConstraint(name = "uk_crew_member_event", columnNames = {"event_id", "user_id"}),
    indexes = {
        @Index(name = "idx_crew_members_crew", columnList = "crew_id"),
        @Index(name = "idx_crew_members_user", columnList = "user_id")
    })
public class CrewMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Crew crew;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CrewRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** Ostatnia wiadomosc czatu ekipy, jaka ta osoba widziala - jak w klanie. */
    @Column(name = "chat_read_id")
    private Long chatReadId;

    protected CrewMember() {
        // wymagany przez JPA
    }

    public CrewMember(Crew crew, User user, CrewRole role, LocalDateTime joinedAt, Long chatReadId) {
        this.crew = crew;
        this.eventId = crew.getEvent().getId();
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
        this.chatReadId = chatReadId;
    }

    /** Nie cofa sie - starsze "przeczytane do" z innej karty nic nie zmienia. */
    public void markChatRead(long upTo) {
        if (chatReadId == null || upTo > chatReadId) {
            chatReadId = upTo;
        }
    }

    public void setRole(CrewRole role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public Crew getCrew() {
        return crew;
    }

    public Long getEventId() {
        return eventId;
    }

    public User getUser() {
        return user;
    }

    public CrewRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public Long getChatReadId() {
        return chatReadId;
    }
}
