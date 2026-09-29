package com.musicclubapp.service;

import com.musicclubapp.dto.BlockedUserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.entity.UserBlock;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.UserBlockRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blokowanie uzytkownikow.
 *
 * <p>Blokada rozcina wszystko miedzy dwiema osobami: znajomosc,
 * zaproszenia, powiadomienia. Potem zadna z nich nie widzi postow ani
 * profilu drugiej i nie moze jej zaprosic. Czat jest tylko dla znajomych,
 * wiec pisac tez juz sie nie da - historia rozmowy zostaje u obu.</p>
 *
 * <p>Zablokowany nie dostaje zadnej informacji. Dla niego profil
 * blokujacego wyglada tak, jakby konta nie bylo, a proba zaproszenia konczy
 * sie tym samym komunikatem co przy "nie przyjmuje zaproszen".</p>
 */
@Service
public class BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final FriendRequestRepository requests;
    private final NotificationService notifications;

    public BlockService(UserBlockRepository blocks, UserRepository users, FriendRequestRepository requests,
                        NotificationService notifications) {
        this.blocks = blocks;
        this.users = users;
        this.requests = requests;
        this.notifications = notifications;
    }

    @Transactional
    public void block(String username, String targetUsername) {
        if (username.equals(targetUsername)) {
            throw OperationNotAllowedException.blockSelf();
        }
        User ja = require(username);
        User on = require(targetUsername);
        if (blocks.existsByBlockerIdAndBlockedId(ja.getId(), on.getId())) {
            return;
        }
        blocks.save(new UserBlock(ja, on, LocalDateTime.now()));

        // Znajomosc i zaproszenia w obie strony
        ja.removeFriend(on);
        requests.find(username, targetUsername).ifPresent(requests::delete);
        requests.find(targetUsername, username).ifPresent(requests::delete);
        notifications.deleteBetween(ja.getId(), on.getId());
        log.info("{} zablokowal(a) {}", username, targetUsername);
    }

    @Transactional
    public void unblock(String username, String targetUsername) {
        User ja = require(username);
        User on = require(targetUsername);
        blocks.findByBlockerIdAndBlockedId(ja.getId(), on.getId()).ifPresent(blocks::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> blockedBy(String username) {
        return blocks.blockedBy(username).stream()
            .map(b -> new BlockedUserResponse(
                b.getBlocked().getUsername(),
                b.getBlocked().getAvatarFileName() == null ? null
                    : PostMapper.UPLOADS_PATH + b.getBlocked().getAvatarFileName(),
                b.getCreatedAt()))
            .toList();
    }

    /** Konta niewidoczne dla tej osoby - zablokowane przez nia i blokujace ja. */
    @Transactional(readOnly = true)
    public Set<Long> hiddenFor(Long userId) {
        return userId == null ? Set.of() : new HashSet<>(blocks.hiddenFor(userId));
    }

    /**
     * To samo w postaci gotowej do "NOT IN (...)" w zapytaniu. Pusta lista
     * w IN to blad skladni w czesci baz - podstawiamy identyfikator, ktorego nie ma.
     */
    @Transactional(readOnly = true)
    public List<Long> hiddenForQuery(Long userId) {
        Set<Long> ukryte = hiddenFor(userId);
        return ukryte.isEmpty() ? List.of(-1L) : List.copyOf(ukryte);
    }

    @Transactional(readOnly = true)
    public boolean eitherWay(Long a, Long b) {
        return a != null && b != null && blocks.existsEitherWay(a, b);
    }

    @Transactional(readOnly = true)
    public boolean blockedByMe(Long me, Long other) {
        return blocks.existsByBlockerIdAndBlockedId(me, other);
    }

    /** Przy usuwaniu konta - blokady w obie strony. */
    @Transactional
    public void deleteAllOf(Long userId) {
        blocks.deleteAllOfUser(userId);
    }

    private User require(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
