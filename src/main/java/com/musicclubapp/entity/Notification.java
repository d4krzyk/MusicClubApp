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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    /** Kto to wywolal. null przy powiadomieniach od samej aplikacji (przypomnienia). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
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

    /**
     * Wydarzenie - tylko przy NotificationType#EVENT_REMINDER. Znika razem
     * z wydarzeniem (baza kasuje powiadomienie sama).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MusicEvent event;

    /** Klan - przy zaproszeniu i wyrzuceniu. Znika razem z klanem. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clan clan;

    /** Komentarz - przy komentarzu pod postem, odpowiedzi i oznaczeniu. Znika razem z komentarzem. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment comment;

    /** Ekipa na koncert - przy prosbach, dolaczeniu i spotkaniach ekipy. Znika razem z ekipa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Crew crew;

    /** Spotkanie z czatu - przy przypomnieniu i odwolaniu. Znika razem ze spotkaniem. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    /** Ile dni zostalo do wydarzenia w chwili przypomnienia: 0 = dzis, 1 = jutro. */
    @Column(name = "days_left")
    private Integer daysLeft;

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

    /** Wzajemne "tak" w trybie Poznawaj - znajomosc z {@code actor}. */
    public static Notification discoverMatch(User recipient, User actor) {
        return new Notification(recipient, actor, NotificationType.DISCOVER_MATCH);
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

    /** Przypomnienie o wydarzeniu, na ktore odbiorca jest zapisany. */
    public static Notification eventReminder(User recipient, MusicEvent event, int daysLeft) {
        Notification n = new Notification(recipient, null, NotificationType.EVENT_REMINDER);
        n.event = event;
        n.daysLeft = daysLeft;
        return n;
    }

    /** Zaproszenie do klanu. */
    public static Notification clanInvite(User recipient, User inviter, Clan clan) {
        Notification n = new Notification(recipient, inviter, NotificationType.CLAN_INVITE);
        n.clan = clan;
        return n;
    }

    /** Wyrzucenie z klanu - bez sprawcy, zeby nie robic z tego narzedzia do klotni. */
    public static Notification clanKicked(User recipient, Clan clan) {
        Notification n = new Notification(recipient, null, NotificationType.CLAN_KICKED);
        n.clan = clan;
        return n;
    }

    /** Prosba o dolaczenie do klanu - dla jednej osoby z zarzadu, z proszacym jako sprawca. */
    public static Notification clanJoinRequest(User manager, User requester, Clan clan) {
        Notification n = new Notification(manager, requester, NotificationType.CLAN_JOIN_REQUEST);
        n.clan = clan;
        return n;
    }

    /** Prosba przyjeta - dla proszacego, bez wskazywania, kto ja przyjal. */
    public static Notification clanRequestAccepted(User requester, Clan clan) {
        Notification n = new Notification(requester, null, NotificationType.CLAN_REQUEST_ACCEPTED);
        n.clan = clan;
        return n;
    }

    /**
     * Ekipa na koncert: prosba (do zakladajacego, sprawca proszacy), przyjecie (do proszacego), dolaczenie (do
     * zakladajacego, sprawca nowa osoba), usuniecie z ekipy (bez sprawcy, jak w klanie). Zawsze z wydarzeniem - po nim
     * dzwonek pisze, na jaki koncert.
     */
    public static Notification crew(NotificationType type, User recipient, User actor, Crew crew) {
        Notification n = new Notification(recipient, actor, type);
        n.crew = crew;
        n.event = crew.getEvent();
        return n;
    }

    /** Komentarz, odpowiedz albo oznaczenie - {@code type} mowi, ktore z trzech. */
    public static Notification comment(NotificationType type, User recipient, User actor, Post post, Comment comment) {
        Notification n = new Notification(recipient, actor, type);
        n.post = post;
        n.comment = comment;
        return n;
    }

    /**
     * Przypomnienie o spotkaniu. Sprawca to druga strona rozmowy (z nia jest spotkanie, do niej prowadzi klikniecie),
     * przy spotkaniu klanu albo ekipy - nikt, a klan albo ekipa jest wpisana.
     */
    public static Notification meetingReminder(User recipient, Meeting meeting) {
        User other = meeting.getClan() != null || meeting.getCrew() != null ? null
            : meeting.getCreator().getId().equals(recipient.getId()) ? meeting.getPartner() : meeting.getCreator();
        Notification n = new Notification(recipient, other, NotificationType.MEETING_REMINDER);
        n.meeting = meeting;
        n.clan = meeting.getClan();
        n.crew = meeting.getCrew();
        return n;
    }

    /** Odwolanie spotkania - dla potwierdzonych, z zakladajacym jako sprawca. */
    public static Notification meetingCancelled(User recipient, User creator, Meeting meeting) {
        Notification n = new Notification(recipient, creator, NotificationType.MEETING_CANCELLED);
        n.meeting = meeting;
        n.clan = meeting.getClan();
        n.crew = meeting.getCrew();
        return n;
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

    public MusicEvent getEvent() {
        return event;
    }

    public Clan getClan() {
        return clan;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public Crew getCrew() {
        return crew;
    }

    public Comment getComment() {
        return comment;
    }

    public Integer getDaysLeft() {
        return daysLeft;
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
