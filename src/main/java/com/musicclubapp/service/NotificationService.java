package com.musicclubapp.service;

import com.musicclubapp.dto.NotificationResponse;
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

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
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

    /** Nowe zgloszenie - powiadamiamy KAZDEGO administratora. */
    @Transactional
    public void reportFiled(List<User> admins, User reporter) {
        for (User admin : admins) {
            if (!admin.getId().equals(reporter.getId())) {
                notificationRepository.save(Notification.report(admin, reporter));
            }
        }
    }

    /** Zgloszenie rozpatrzone - powiadomienie dla zglaszajacego. */
    @Transactional
    public void reportResolved(User reporter, User admin) {
        if (reporter.getId().equals(admin.getId())) {
            return;
        }
        notificationRepository.save(Notification.reportResolved(reporter, admin));
    }

    /** Ktos wyslal zaproszenie do znajomych. */
    @Transactional
    public void friendRequestSent(User recipient, User actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.save(Notification.friendRequest(recipient, actor));
    }

    /** Zaproszenie przestalo istniec (odrzucone albo anulowane). */
    @Transactional
    public void friendRequestGone(User recipient, User actor) {
        notificationRepository.deleteByType(
            recipient.getId(), actor.getId(), NotificationType.FRIEND_REQUEST);
    }

    /** Znajomosc doszla do skutku - powiadamiamy te osobe, ktora czekala. */
    @Transactional
    public void friendshipFormed(User recipient, User actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.deleteByType(
            actor.getId(), recipient.getId(), NotificationType.FRIEND_REQUEST);
        notificationRepository.save(Notification.friendAccepted(recipient, actor));
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
