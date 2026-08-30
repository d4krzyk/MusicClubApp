package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.BanRequest;
import com.musicclubapp.dto.RelatedAccountResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Uprawnienia administratora wobec KONT: usuwanie, kary i powiazania sieciowe. */
@Service
public class UserModerationService {

    private static final Logger log = LoggerFactory.getLogger(UserModerationService.class);

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final UserMapper userMapper;

    /* Moduly, ktore sprzataja po kasowanym koncie. */
    private final NotificationService notifications;
    private final ReactionService reactions;
    private final PostService posts;
    private final FriendService friends;
    private final MessageService messages;
    private final ReportService reports;
    private final NetworkService network;
    private final PlaylistService playlists;
    private final UserService users;

    public UserModerationService(UserRepository userRepository,
                                 ReportRepository reportRepository,
                                 UserMapper userMapper,
                                 NotificationService notifications,
                                 ReactionService reactions,
                                 PostService posts,
                                 FriendService friends,
                                 MessageService messages,
                                 ReportService reports,
                                 NetworkService network,
                                 PlaylistService playlists,
                                 UserService users) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.userMapper = userMapper;
        this.notifications = notifications;
        this.reactions = reactions;
        this.posts = posts;
        this.friends = friends;
        this.messages = messages;
        this.reports = reports;
        this.network = network;
        this.playlists = playlists;
        this.users = users;
    }

    /** Kasuje konto razem ze wszystkim, co po nim zostalo. */
    @Transactional
    public void deleteUser(String adminUsername, Long id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        // 1. Powiadomienia - wskazuja i na konto, i na posty kasowane nizej
        notifications.deleteAllOf(id);

        // 2. Reakcje tej osoby pod CUDZYMI postami - te posty maja zostac
        reactions.deleteAllOf(id);

        // 3. Wlasne posty razem ze zdjeciami z dysku
        int usunietychPostow = posts.deleteAllOf(id);

        /* 4. */
        friends.deleteAllOf(target);

        /* 5. */
        messages.deleteAllOf(id);

        // 6. Zgloszenia zlozone przez to konto i te na nie
        reports.deleteAllOf(id);

        /* 7. */
        network.deleteAllOf(id);

        /* 8. */
        target.getFavoriteArtists().clear();
        target.getFavoriteTracks().clear();

        // 9. Gablotka playlist
        playlists.deleteAllOf(id);

        // 10. Zdjecie profilowe z dysku
        users.deleteAvatarOf(target);

        userRepository.delete(target);
        log.info("Administrator {} usunal konto {} (postow: {})",
            adminUsername, target.getUsername(), usunietychPostow);
    }

    /** Termin konca kary dla obu rodzajow zakazu - jedno miejsce na te regule. */
    private LocalDateTime banUntil(Integer hours, Boolean forever) {
        if (Boolean.TRUE.equals(forever)) {
            return User.FOREVER;
        }
        return hours == null ? null : LocalDateTime.now().plusHours(hours);
    }

    /** Czytelny opis kary do logu - w logu "9999-12-31" wygladaloby na usterke. */
    private String describeBan(LocalDateTime until) {
        if (until == null) {
            return "zdjety";
        }
        return User.isForever(until) ? "bezterminowo" : "do " + until;
    }

    /** Naklada albo zdejmuje kare - jedna metoda na oba rodzaje. */
    @Transactional
    public AdminUserResponse setBan(String adminUsername, Long id,
                                    BanKind kind, BanRequest payload) {

        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        LocalDateTime until = banUntil(payload.hours(), payload.forever());
        target.setBannedUntil(kind, until);

        log.info("Administrator {} ustawil kare {} dla konta {}: {}",
            adminUsername, kind, target.getUsername(), describeBan(until));

        return toResponse(userRepository.save(target));
    }

    /** Konta logujace sie z tych samych adresow co wskazane. */
    @Transactional(readOnly = true)
    public List<RelatedAccountResponse> relatedAccounts(Long id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        return network.relatedAccounts(target.getId()).stream()
            .map(entry -> new RelatedAccountResponse(
                entry.getUser().getId(),
                entry.getUser().getUsername(),
                entry.getUser().getAvatarFileName() == null
                    ? null
                    : PostMapper.UPLOADS_PATH + entry.getUser().getAvatarFileName(),
                entry.getAddress(),
                entry.getLastSeenAt(),
                entry.getLoginCount()))
            .toList();
    }

    /** Adresy, z ktorych logowalo sie dane konto - do skopiowania w blokade. */
    @Transactional(readOnly = true)
    public List<RelatedAccountResponse> addressesOf(Long id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        return network.addressesOf(target.getId()).stream()
            .map(entry -> new RelatedAccountResponse(
                target.getId(),
                target.getUsername(),
                null,
                entry.getAddress(),
                entry.getLastSeenAt(),
                entry.getLoginCount()))
            .toList();
    }

    private AdminUserResponse toResponse(User user) {
        return userMapper.toAdminResponse(
            user,
            reportRepository.countByReportedIdAndStatus(user.getId(), ReportStatus.RESOLVED));
    }
}
