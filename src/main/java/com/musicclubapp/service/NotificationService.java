package com.musicclubapp.service;

import com.musicclubapp.dto.NotificationResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.Comment;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.Notification;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.mapper.NotificationMapper;
import com.musicclubapp.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Powiadomienia: powstawanie, czytanie i sprzatanie. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final PushService push;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationMapper notificationMapper,
                               PushService push) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.push = push;
    }

    /**
     * Zapis w dzwonku i - dla wazniejszych rodzajow - powiadomienie na telefon.
     * Reakcje zostaja tylko w dzwonku: przy popularnym poscie telefon
     * brzeczalby co chwile.
     */
    private void zapisz(Notification n) {
        notificationRepository.save(n);
        PushService.Message wiadomosc = naTelefon(n);
        if (wiadomosc != null) {
            push.send(n.getRecipient().getId(), wiadomosc);
        }
    }

    private PushService.Message naTelefon(Notification n) {
        String link = notificationMapper.link(n);
        String kto = n.getActor() != null ? n.getActor().getUsername() : null;
        return switch (n.getType()) {
            case FRIEND_REQUEST -> new PushService.Message("push.friendRequest.title", null,
                "push.friendRequest.body", new Object[] {kto}, link, "friend-" + kto);
            case FRIEND_ACCEPTED -> new PushService.Message("push.friendAccepted.title", null,
                "push.friendAccepted.body", new Object[] {kto}, link, "friend-" + kto);
            case REPORT -> new PushService.Message("push.report.title", null,
                "push.report.body", new Object[] {kto}, link, "report");
            case REPORT_RESOLVED -> new PushService.Message("push.reportResolved.title", null,
                "push.reportResolved.body", null, link, "report-resolved");
            case EVENT_REMINDER -> przypomnienie(n, link);
            case CLAN_INVITE -> new PushService.Message("push.clanInvite.title", null,
                "push.clanInvite.body", new Object[] {kto, n.getClan().getName()}, link, "clan-invite-" + n.getClan().getId());
            case CLAN_KICKED -> new PushService.Message("push.clanKicked.title", null,
                "push.clanKicked.body", new Object[] {n.getClan().getName()}, link, "clan-kicked");
            case CLAN_JOIN_REQUEST -> new PushService.Message("push.clanRequest.title", null,
                "push.clanRequest.body", new Object[] {kto, n.getClan().getName()}, link,
                "clan-request-" + n.getClan().getId());
            case CLAN_REQUEST_ACCEPTED -> new PushService.Message("push.clanAccepted.title", null,
                "push.clanAccepted.body", new Object[] {n.getClan().getName()}, link, "clan-accepted");
            case POST_COMMENT -> new PushService.Message("push.comment.title", null,
                "push.comment.body", new Object[] {kto}, link, "comment-post-" + n.getPost().getId());
            case COMMENT_REPLY -> new PushService.Message("push.commentReply.title", null,
                "push.commentReply.body", new Object[] {kto}, link, "comment-reply-" + n.getComment().getId());
            case COMMENT_MENTION -> new PushService.Message("push.commentMention.title", null,
                "push.commentMention.body", new Object[] {kto}, link, "comment-mention-" + n.getComment().getId());
            case REACTION -> null;
        };
    }

    /** "Jutro: Nocny koncert" / "Progresja · 20:00". Jeden znacznik na wydarzenie - "jutro" zastepuje "za 3 dni". */
    private static PushService.Message przypomnienie(Notification n, String link) {
        MusicEvent e = n.getEvent();
        int dni = n.getDaysLeft() == null ? 0 : n.getDaysLeft();
        String tytul = dni == 0 ? "push.reminder.today" : dni == 1 ? "push.reminder.tomorrow" : "push.reminder.days";
        String miejsce = e.getVenueName() == null ? "" : e.getVenueName();
        String godzina = e.getStartTime() == null ? null : e.getStartTime().toString().substring(0, 5);
        return new PushService.Message(tytul, new Object[] {e.getName(), dni},
            godzina == null ? "push.reminder.place" : "push.reminder.placeTime",
            new Object[] {miejsce, godzina}, link, "event-" + e.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Powstawanie                                                        */
    /* ------------------------------------------------------------------ */

    /** Ktos zareagowal na cudzy post. */
    @Transactional
    public void reactionAdded(Post post, User actor, ReactionType reactionType) {
        User recipient = post.getAuthor();
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        notificationRepository
            .find(recipient.getId(), actor.getId(), post.getId(), NotificationType.REACTION)
            .ifPresentOrElse(
                existing -> existing.refresh(reactionType),
                () -> notificationRepository.save(
                    Notification.reaction(recipient, actor, post, reactionType)));
    }

    /** Reakcja zostala cofnieta - powiadomienie o niej przestaje byc prawdziwe. */
    @Transactional
    public void reactionRemoved(Post post, User actor) {
        notificationRepository.deleteMatching(
            post.getAuthor().getId(), actor.getId(), post.getId(), NotificationType.REACTION);
    }

    /** Ktos skomentowal post odbiorcy - w dzwonku i na telefonie (jeden baner na post). */
    @Transactional
    public void postCommented(User recipient, User actor, Post post, Comment comment) {
        zapisz(Notification.comment(NotificationType.POST_COMMENT, recipient, actor, post, comment));
    }

    /** Ktos odpowiedzial na komentarz odbiorcy. */
    @Transactional
    public void commentReply(User recipient, User actor, Post post, Comment comment) {
        zapisz(Notification.comment(NotificationType.COMMENT_REPLY, recipient, actor, post, comment));
    }

    /** Ktos oznaczyl odbiorce w komentarzu. */
    @Transactional
    public void commentMention(User recipient, User actor, Post post, Comment comment) {
        zapisz(Notification.comment(NotificationType.COMMENT_MENTION, recipient, actor, post, comment));
    }

    /** Nowe zgloszenie - powiadamiamy KAZDEGO administratora. */
    @Transactional
    public void reportFiled(List<User> admins, User reporter) {
        for (User admin : admins) {
            if (!admin.getId().equals(reporter.getId())) {
                zapisz(Notification.report(admin, reporter));
            }
        }
    }

    /** Zgloszenie rozpatrzone - powiadomienie dla zglaszajacego. */
    @Transactional
    public void reportResolved(User reporter, User admin) {
        if (reporter.getId().equals(admin.getId())) {
            return;
        }
        zapisz(Notification.reportResolved(reporter, admin));
    }

    /** Ktos wyslal zaproszenie do znajomych. */
    @Transactional
    public void friendRequestSent(User recipient, User actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        zapisz(Notification.friendRequest(recipient, actor));
    }

    /** Zaproszenie przestalo istniec (odrzucone albo anulowane). */
    @Transactional
    public void friendRequestGone(User recipient, User actor) {
        notificationRepository.deleteByType(
            recipient.getId(), actor.getId(), NotificationType.FRIEND_REQUEST);
    }

    /** Blokada - znika wszystko, co jedna z tych osob zostawila w dzwonku drugiej. */
    @Transactional
    public void deleteBetween(Long a, Long b) {
        notificationRepository.deleteBetween(a, b);
    }

    /** Zaproszenie do klanu - w dzwonku i na telefonie. */
    @Transactional
    public void clanInvited(User recipient, User inviter, Clan clan) {
        notificationRepository.deleteClanInvites(recipient.getId(), clan.getId());
        zapisz(Notification.clanInvite(recipient, inviter, clan));
    }

    /** Zaproszenie przestalo czekac. */
    @Transactional
    public void clanInviteGone(Long recipientId, Long clanId) {
        notificationRepository.deleteClanInvites(recipientId, clanId);
    }

    /** Ktos prosi o dolaczenie do klanu - powiadomienie dla kazdej osoby z zarzadu (w dzwonku i na telefonie). */
    @Transactional
    public void clanJoinRequested(List<User> managers, User requester, Clan clan) {
        notificationRepository.deleteClanJoinRequests(requester.getId(), clan.getId());
        for (User manager : managers) {
            zapisz(Notification.clanJoinRequest(manager, requester, clan));
        }
    }

    /** Prosba przestala czekac (przyjeta, odrzucona, cofnieta) - znika z dzwonka zarzadu. */
    @Transactional
    public void clanRequestGone(Long requesterId, Long clanId) {
        notificationRepository.deleteClanJoinRequests(requesterId, clanId);
    }

    /** Prosba przyjeta - dla proszacego, bez wskazywania, kto ja przyjal. */
    @Transactional
    public void clanRequestAccepted(User requester, Clan clan) {
        zapisz(Notification.clanRequestAccepted(requester, clan));
    }

    /** Wyrzucenie z klanu - jedno powiadomienie, bez podawania kto. */
    @Transactional
    public void clanKicked(User recipient, Clan clan) {
        zapisz(Notification.clanKicked(recipient, clan));
    }

    /** Przed skasowaniem postow klanu. */
    @Transactional
    public void clanPostsDeleted(Long clanId) {
        notificationRepository.deleteByPostsOfClan(clanId);
    }

    /**
     * Przypomnienie o wydarzeniu. Poprzednie przypomnienie o tym samym
     * wydarzeniu znika - w dzwonku ma byc aktualne "jutro", a nie jeszcze
     * i "za 3 dni".
     */
    @Transactional
    public void eventReminder(User recipient, MusicEvent event, int daysLeft) {
        notificationRepository.deleteReminders(recipient.getId(), event.getId());
        zapisz(Notification.eventReminder(recipient, event, daysLeft));
    }

    /** Rezygnacja z wydarzenia - przypomnienie o nim przestaje miec sens. */
    @Transactional
    public void eventRemindersGone(Long recipientId, Long eventId) {
        notificationRepository.deleteReminders(recipientId, eventId);
    }

    /** Znajomosc doszla do skutku - powiadamiamy te osobe, ktora czekala. */
    @Transactional
    public void friendshipFormed(User recipient, User actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.deleteByType(
            actor.getId(), recipient.getId(), NotificationType.FRIEND_REQUEST);
        zapisz(Notification.friendAccepted(recipient, actor));
    }

    /* ------------------------------------------------------------------ */
    /*  Czytanie                                                           */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public Page<NotificationResponse> forUser(String username, Pageable pageable) {
        return notificationRepository.forUser(username, pageable)
            .map(notificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return notificationRepository.countUnread(username);
    }

    /** Oznacza JEDNO powiadomienie jako przeczytane. */
    @Transactional
    public void markRead(String username, Long id) {
        notificationRepository.findById(id)
            .filter(n -> n.getRecipient().getUsername().equals(username))
            .ifPresent(Notification::markRead);
    }

    @Transactional
    public int markAllRead(String username) {
        return notificationRepository.markAllRead(username);
    }

    /** Kasuje JEDNO powiadomienie - na zyczenie odbiorcy. */
    @Transactional
    public void delete(String username, Long id) {
        notificationRepository.findById(id)
            .filter(n -> n.getRecipient().getUsername().equals(username))
            .ifPresent(notificationRepository::delete);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Przed skasowaniem WSZYSTKICH postow jednego autora. */
    @Transactional
    public void postsOfAuthorDeleted(Long authorId) {
        notificationRepository.deleteByPostAuthorId(authorId);
    }

    /** Przed usunieciem posta - inaczej klucz obcy nie pozwoli go skasowac. */
    @Transactional
    public void postDeleted(Long postId) {
        notificationRepository.deleteByPostId(postId);
    }

    /** Kasuje powiadomienia konta - przy usuwaniu uzytkownika. */
    @Transactional
    public void deleteAllOf(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }
}
