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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Jedno powiadomienie dla jednego uzytkownika. */
@Entity
@Table(
    name = "notifications",
    indexes = {
        /*
         * Kazde wejscie na strone pyta o liczbe nieprzeczytanych powiadomien DANEGO uzytkownika.
         */
        @Index(name = "idx_notifications_recipient", columnList = "recipient_id, created_at")
    })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Komu sie wyswietli. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Kto to wywolal. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    /** Post, ktorego dotyczy powiadomienie - wypelniony tylko przy NotificationType#REACTION. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /**
     * Ktora reakcja - tylko przy NotificationType#REACTION. "Ktos zareagowal 🔥 na Twoj post"
     * niesie wiecej niz samo "ktos zareagowal", a kosztuje jedna kolumne.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 16)
    private ReactionType reactionType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Kiedy przeczytane; null znaczy "jeszcze nie". */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    private Notification(User recipient, User actor, NotificationType type) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
    }

    /** Ktos zareagowal na post odbiorcy. */
    public static Notification reaction(User recipient, User actor, Post post, ReactionType reactionType) {
        Notification n = new Notification(recipient, actor, NotificationType.REACTION);
        n.post = post;
        n.reactionType = reactionType;
        return n;
    }

    /** Ktos wyslal odbiorcy zaproszenie do znajomych. */
    public static Notification friendRequest(User recipient, User actor) {
        return new Notification(recipient, actor, NotificationType.FRIEND_REQUEST);
    }

    /** Ktos przyjal zaproszenie odbiorcy (albo znajomosc powstala od razu). */
    public static Notification friendAccepted(User recipient, User actor) {
        return new Notification(recipient, actor, NotificationType.FRIEND_ACCEPTED);
    }

    /**
     * Ktos zlozyl zgloszenie - dla administratora. actor to zglaszajacy, a nie osoba zgloszona.
     */
    public static Notification report(User admin, User reporter) {
        return new Notification(admin, reporter, NotificationType.REPORT);
    }

    /** Zgloszenie rozpatrzone - dla zglaszajacego, z administratorem jako sprawca. */
    public static Notification reportResolved(User reporter, User admin) {
        return new Notification(reporter, admin, NotificationType.REPORT_RESOLVED);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Odswieza powiadomienie zamiast tworzyc drugie. */
    public void refresh(ReactionType reactionType) {
        this.reactionType = reactionType;
        this.createdAt = LocalDateTime.now();
        this.readAt = null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public User getActor() {
        return actor;
    }

    public NotificationType getType() {
        return type;
    }

    public Post getPost() {
        return post;
    }

    public ReactionType getReactionType() {
        return reactionType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
