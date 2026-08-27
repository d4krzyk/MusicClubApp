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

/**
 * Jedno powiadomienie dla jednego uzytkownika.
 *
 * <p><b>Dlaczego osobna tabela, a nie wyliczanie w locie.</b> Da sie
 * teoretycznie policzyc "co sie zdarzylo od ostatniej wizyty" z reakcji
 * i zaproszen. Tyle ze powiadomienie ma wlasny stan - jest przeczytane albo
 * nie - i tego nie da sie wyprowadzic z niczego innego. Zapis jest tu
 * jedynym uczciwym rozwiazaniem.</p>
 *
 * <p><b>Kolejna relacja {@code ManyToOne}</b> - i to az trzy w jednej encji:
 * odbiorca, sprawca i (przy reakcji) post.</p>
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        /*
         * Kazde wejscie na strone pyta o liczbe nieprzeczytanych powiadomien
         * DANEGO uzytkownika. Bez indeksu baza przegladalaby cala tabele -
         * a ta rosnie z kazda reakcja w calym serwisie.
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

    /**
     * Kto to wywolal.
     *
     * <p>Nigdy nie jest to sam odbiorca - powiadomienie o wlasnym dzialaniu
     * nie ma sensu i nie powstaje (patrz {@code NotificationService}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    /**
     * Post, ktorego dotyczy powiadomienie - wypelniony tylko przy
     * {@link NotificationType#REACTION}.
     *
     * <p><b>To jest prawdziwy klucz obcy</b>, a nie luzny numer. Roznica jest
     * istotna: baza sama pilnuje, ze powiadomienie nie wskaze posta, ktorego
     * juz nie ma. Przy zwyklej kolumnie {@code Long} skasowanie posta
     * zostawialoby powiadomienie prowadzace donikad - a klikniecie w nie
     * konczyloby sie bledem 404 zamiast czegokolwiek sensownego.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /**
     * Ktora reakcja - tylko przy {@link NotificationType#REACTION}.
     *
     * <p>"Ktos zareagowal 🔥 na Twoj post" niesie wiecej niz samo "ktos
     * zareagowal", a kosztuje jedna kolumne.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 16)
    private ReactionType reactionType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Kiedy przeczytane; {@code null} znaczy "jeszcze nie".
     *
     * <p>Data zamiast flagi {@code boolean} - kosztuje tyle samo, a pozwala
     * kiedys pokazac "przeczytane 3 dni temu" albo posprzatac stare wpisy.</p>
     */
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Odswieza powiadomienie zamiast tworzyc drugie.
     *
     * <p>Uzywane, gdy ta sama osoba zmienia zdanie co do reakcji. Bez tego
     * jedna osoba klikajaca kolejno trzy emotki wyprodukowalaby trzy
     * powiadomienia o tym samym poscie - a to najkrotsza droga do tego,
     * zeby uzytkownik przestal je czytac.</p>
     */
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
