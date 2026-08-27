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

/**
 * Powiadomienia: powstawanie, czytanie i sprzatanie.
 *
 * <p><b>Jedno miejsce na wszystkie reguly.</b> Powiadomienia wywoluja trzy
 * rozne serwisy (reakcje, znajomi, moderacja) i gdyby kazdy z nich sam
 * decydowal, czy wpis ma powstac, regula "nie powiadamiam samego siebie"
 * musialaby byc powtorzona w kazdym z nich. Wystarczy pomylic sie raz.</p>
 *
 * <p>Zadna metoda tej klasy <b>nie przerywa dzialania wywolujacego</b>:
 * powiadomienie jest dodatkiem do zdarzenia, a nie jego warunkiem. Reakcja
 * ma sie zapisac takze wtedy, gdy z powiadomieniem cos pojdzie nie tak.</p>
 */
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

    /**
     * Ktos zareagowal na cudzy post.
     *
     * <p>Dwie reguly siedza tutaj i nigdzie indziej:</p>
     * <ul>
     *   <li><b>reakcja na wlasny post nie powiadamia</b> - wiadomo, co sie
     *       samemu zrobilo;</li>
     *   <li><b>zmiana zdania odswieza wpis zamiast dokladac drugi</b> - jedna
     *       osoba klikajaca kolejno trzy emotki ma zostawic jedno
     *       powiadomienie, a nie trzy.</li>
     * </ul>
     */
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

    /**
     * Reakcja zostala cofnieta - powiadomienie o niej przestaje byc prawdziwe.
     *
     * <p>Zostawienie go znaczyloby, ze klikniecie prowadzi do posta, pod
     * ktorym nie ma juz sladu po tej reakcji.</p>
     */
    @Transactional
    public void reactionRemoved(Post post, User actor) {
        notificationRepository.deleteMatching(
            post.getAuthor().getId(), actor.getId(), post.getId(), NotificationType.REACTION);
    }

    /** Ktos wyslal zaproszenie do znajomych. */
    @Transactional
    public void friendRequestSent(User recipient, User actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.save(Notification.friendRequest(recipient, actor));
    }

    /**
     * Zaproszenie przestalo istniec (odrzucone albo anulowane).
     *
     * <p>Powiadomienie o zaproszeniu, ktorego juz nie ma, prowadzi na strone
     * znajomych, gdzie nic nie czeka - a to wyglada jak usterka.</p>
     */
    @Transactional
    public void friendRequestGone(User recipient, User actor) {
        notificationRepository.deleteByType(
            recipient.getId(), actor.getId(), NotificationType.FRIEND_REQUEST);
    }

    /**
     * Znajomosc doszla do skutku - powiadamiamy te osobe, ktora czekala.
     *
     * <p>Przy okazji kasujemy powiadomienie o samym zaproszeniu: skoro
     * zostalo przyjete, nie ma juz czego przyjmowac.</p>
     */
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

    /**
     * Oznacza JEDNO powiadomienie jako przeczytane.
     *
     * <p>Cudzego powiadomienia nie da sie tknac - sprawdzamy odbiorce, a nie
     * tylko identyfikator. Bez tego wystarczyloby zgadnac numer, zeby
     * czyscic komus dzwonek.</p>
     */
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

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Przed usunieciem posta - inaczej klucz obcy nie pozwoli go skasowac. */
    @Transactional
    public void postDeleted(Long postId) {
        notificationRepository.deleteByPostId(postId);
    }

    /** Przed usunieciem konta - w obie strony: co dostal i co wywolal. */
    @Transactional
    public void userDeleted(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }
}
