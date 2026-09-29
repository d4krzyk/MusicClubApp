package com.musicclubapp.service;

import com.musicclubapp.dto.FriendCardResponse;
import com.musicclubapp.dto.FriendRequestResponse;
import com.musicclubapp.dto.FriendshipStatus;
import com.musicclubapp.dto.PendingRequestsResponse;
import com.musicclubapp.dto.SuggestionResponse;
import com.musicclubapp.entity.FriendRequest;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.FriendRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Znajomi: zapraszanie, przyjmowanie i lista. */
@Service
public class FriendService {

    private final UserRepository userRepository;
    private final FriendRequestRepository requestRepository;

    private final NotificationService notifications;
    private final PresenceService presence;
    private final PrivacyService privacy;
    private final BlockService blocks;

    public FriendService(UserRepository userRepository,
                         FriendRequestRepository requestRepository,
                         NotificationService notifications,
                         PresenceService presence,
                         PrivacyService privacy,
                         BlockService blocks) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.notifications = notifications;
        this.privacy = privacy;
        this.blocks = blocks;
        this.presence = presence;
    }

    /** Wysyla zaproszenie do znajomych. */
    @Transactional
    public boolean invite(String username, String targetUsername) {
        if (username.equals(targetUsername)) {
            throw OperationNotAllowedException.invitationToSelf();
        }

        User ja = user(username);
        User on = user(targetUsername);

        if (userRepository.areFriends(username, targetUsername)) {
            throw OperationNotAllowedException.alreadyFriends();
        }
        if (requestRepository.find(username, targetUsername).isPresent()) {
            throw OperationNotAllowedException.invitationAlreadySent();
        }

        // On zaprosil mnie wczesniej -> po prostu przyjmujemy tamto zaproszenie
        var fromThem = requestRepository.find(targetUsername, username);
        if (fromThem.isPresent()) {
            merge(ja, on, fromThem.get());
            return true;
        }

        // Jego ustawienia ("nikt", "znajomi znajomych") i blokady - jeden komunikat na wszystko
        if (!privacy.canInvite(ja, on)) {
            throw OperationNotAllowedException.cannotInvite();
        }

        requestRepository.save(new FriendRequest(ja, on));
        notifications.friendRequestSent(on, ja);
        return false;
    }

    /** Przyjmuje zaproszenie skierowane DO MNIE. */
    @Transactional
    public void accept(Long invitationId, String username) {
        FriendRequest invitation = invitation(invitationId);

        /* Kluczowe sprawdzenie: przyjac moze WYLACZNIE odbiorca. */
        if (!invitation.getRecipient().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesInvitation();
        }
        if (blocks.eitherWay(invitation.getSender().getId(), invitation.getRecipient().getId())) {
            throw OperationNotAllowedException.cannotInvite();
        }

        merge(invitation.getSender(), invitation.getRecipient(), invitation);
    }

    /** Odrzuca zaproszenie do mnie albo anuluje moje wlasne. */
    @Transactional
    public void rejectOrCancel(Long invitationId, String username) {
        FriendRequest invitation = invitation(invitationId);

        boolean myInvitation = invitation.getSender().getUsername().equals(username)
            || invitation.getRecipient().getUsername().equals(username);

        if (!myInvitation) {
            throw OperationNotAllowedException.someoneElsesInvitation();
        }

        requestRepository.delete(invitation);

        /* Powiadomienie o zaproszeniu ma zniknac razem z nim. */
        notifications.friendRequestGone(invitation.getRecipient(), invitation.getSender());
    }

    /** Usuwa znajomosc - u obu osob naraz. */
    @Transactional
    public void removeFriend(String username, String targetUsername) {
        User ja = user(username);
        User on = user(targetUsername);

        ja.removeFriend(on);
        userRepository.save(ja);
        userRepository.save(on);
    }

    /** Lista znajomych danej osoby, od najbardziej powiazanych z ogladajacym. */
    @Transactional(readOnly = true)
    public Page<FriendCardResponse> friends(String whose, String viewer, Pageable pageable) {
        // Sprawdzamy, czy taka osoba w ogole istnieje - inaczej pusta lista
        // wygladalaby jak "ten uzytkownik nie ma znajomych"
        user(whose);

        // Osoby zablokowane przez ogladajacego i blokujace go - nie pokazujemy
        List<Long> ukryci = blocks.hiddenForQuery(user(viewer).getId());
        return userRepository.friendsRanked(whose, viewer, ukryci, pageable)
            .map(this::toCard);
    }

    /** Proponowani znajomi: cala spolecznosc, od najlepiej dopasowanych. */
    @Transactional(readOnly = true)
    public List<SuggestionResponse> suggestions(String username, int limit) {
        user(username);

        int safeLimit = Math.max(1, Math.min(limit, 60));

        return userRepository
            .friendSuggestions(username, PageRequest.of(0, safeLimit)).stream()
            .map(w -> new SuggestionResponse(
                w.getUsername(),
                avatarUrl(w.getAvatarFileName()),
                w.getSharedFriends(),
                w.getSharedArtists(),
                w.getSharedGenres(),
                w.getAlreadyFriend(),
                // "dopasowany" znaczy: cokolwiek nas laczy. Przy wyniku 0
                // karta trafia do sekcji "pozostale osoby"
                w.getScore() > 0))
            .toList();
    }

    /** Zaproszenia oczekujace - przychodzace i wyslane naraz. */
    @Transactional(readOnly = true)
    public PendingRequestsResponse pending(String username) {
        List<FriendRequestResponse> incoming = requestRepository.incoming(username).stream()
            .map(z -> toResponse(z, z.getSender()))
            .toList();

        List<FriendRequestResponse> outgoing = requestRepository.outgoing(username).stream()
            .map(z -> toResponse(z, z.getRecipient()))
            .toList();

        return new PendingRequestsResponse(incoming, outgoing);
    }

    /** W jakiej relacji jest ogladajacy z dana osoba. */
    @Transactional(readOnly = true)
    public FriendshipStatus status(String viewer, String whose) {
        if (viewer.equals(whose)) {
            return FriendshipStatus.SELF;
        }
        if (userRepository.areFriends(viewer, whose)) {
            return FriendshipStatus.FRIENDS;
        }
        if (requestRepository.find(viewer, whose).isPresent()) {
            return FriendshipStatus.REQUEST_SENT;
        }
        if (requestRepository.find(whose, viewer).isPresent()) {
            return FriendshipStatus.REQUEST_RECEIVED;
        }
        return FriendshipStatus.NONE;
    }

    /** Ile zaproszen czeka na moja odpowiedz - liczba przy pozycji w menu. */
    @Transactional(readOnly = true)
    public long countPending(String username) {
        return requestRepository.countByRecipientUsername(username);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Zrywa wszystkie wiezi konta - zaproszenia i znajomosci - przy jego usuwaniu. */
    @Transactional
    public void deleteAllOf(User user) {
        requestRepository.deleteBySenderIdOrRecipientId(user.getId(), user.getId());

        userRepository.removeFriendshipsWith(user.getId());
        user.getFriends().clear();
    }

    // ----------------------------------------------------------------------

    /** Laczy dwie osoby w znajomych i kasuje zuzyte zaproszenie. */
    private void merge(User a, User b, FriendRequest invitation) {
        a.addFriend(b);
        userRepository.save(a);
        userRepository.save(b);

        notifications.friendshipFormed(a, b);

        /* Zaproszenie znika po przyjeciu - tabela friend_requests trzyma WYLACZNIE oczekujace. */
        requestRepository.delete(invitation);
    }

    private FriendCardResponse toCard(FriendRow row) {
        return new FriendCardResponse(
            row.getUsername(),
            avatarUrl(row.getAvatarFileName()),
            row.getSharedFriends(),
            // Kto ukrywa swoja aktywnosc, jest dla innych po prostu "niedostepny"
            row.getShowOnline() ? presence.of(row.getLastSeenAt()) : presence.hidden());
    }

    private FriendRequestResponse toResponse(FriendRequest invitation, User otherSide) {
        return new FriendRequestResponse(
            invitation.getId(),
            otherSide.getUsername(),
            avatarUrl(otherSide.getAvatarFileName()),
            invitation.getCreatedAt());
    }

    /** Baza trzyma nazwe pliku, na zewnatrz wychodzi gotowy adres - jak przy postach. */
    private String avatarUrl(String fileName) {
        return fileName == null ? null : PostMapper.UPLOADS_PATH + fileName;
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    private FriendRequest invitation(Long id) {
        return requestRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("friendRequest", id));
    }
}
