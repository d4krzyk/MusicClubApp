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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Wiadomosc na czacie ekipy: tekst albo spotkanie (miejsce zbiorki, godzina). Usunieta zostaje jako slad - jak w klanie.
 */
@Entity
@Table(name = "crew_messages", indexes = @Index(name = "idx_crew_messages_crew", columnList = "crew_id, id"))
public class CrewMessage {

    public static final int MAX_CONTENT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Crew crew;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = MAX_CONTENT)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Meeting meeting;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected CrewMessage() {
        // wymagany przez JPA
    }

    public CrewMessage(Crew crew, User sender, String content) {
        this.crew = crew;
        this.sender = sender;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void attachMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public void delete(LocalDateTime now) {
        if (deletedAt == null) {
            content = "";
            meeting = null;
            deletedAt = now;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Crew getCrew() {
        return crew;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
