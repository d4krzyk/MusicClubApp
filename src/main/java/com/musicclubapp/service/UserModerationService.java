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
    private final NetworkService network;

    /* Kolejnosc krokow przy kasowaniu konta - wspolna z ta, ktora ma wlasciciel konta. */
    private final AccountDeletionService deletion;

    public UserModerationService(UserRepository userRepository,
                                 ReportRepository reportRepository,
                                 UserMapper userMapper,
                                 NetworkService network,
                                 AccountDeletionService deletion) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.userMapper = userMapper;
        this.network = network;
        this.deletion = deletion;
    }

    /** Kasuje konto razem ze wszystkim, co po nim zostalo. */
    @Transactional
    public void deleteUser(String adminUsername, Long id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        deletion.erase(target);
        log.info("Administrator {} usunal konto {}", adminUsername, target.getUsername());
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
