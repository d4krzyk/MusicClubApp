package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

/** Odpowiedz jednej osoby na spotkanie: "bede" albo "nie dam rady" - i czy dostala juz przypomnienie. */
@Entity
@Table(name = "meeting_attendees",
    uniqueConstraints = @UniqueConstraint(name = "uk_meeting_attendee", columnNames = {"meeting_id", "user_id"}))
public class MeetingAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MeetingStatus status;

    /** Kiedy poszlo przypomnienie; ustawiane tez od razu, gdy ktos potwierdza juz po chwili przypomnienia. */
    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MeetingAttendee() {
        // wymagany przez JPA
    }

    public MeetingAttendee(Meeting meeting, User user, MeetingStatus status, Instant now) {
        this.meeting = meeting;
        this.user = user;
        this.updatedAt = now;
        setStatus(status, now);
    }

    /**
     * Nowa odpowiedz. "Bede" po chwili przypomnienia nie wysyla go od razu (osoba wie, ze spotkanie jest za chwile);
     * zmiana na "nie dam rady" i z powrotem nie wysyla drugiego.
     */
    public void setStatus(MeetingStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
        if (status == MeetingStatus.GOING && remindedAt == null
            && meeting.getRemindAt() != null && !now.isBefore(meeting.getRemindAt())) {
            remindedAt = now;
        }
    }

    public void markReminded(Instant now) {
        remindedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public User getUser() {
        return user;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
