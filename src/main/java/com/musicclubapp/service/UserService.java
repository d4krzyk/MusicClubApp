package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.ChangeRoleRequest;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.TermsStatusResponse;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.repository.CountByUser;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

/** Logika biznesowa zwiazana z uzytkownikami. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final FileStorageService fileStorage;
    private final ReportRepository reportRepository;
    private final EmailVerificationService emailVerification;
    private final AccountLinks accountLinks;
    private final Legal legal;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper,
                       FileStorageService fileStorage,
                       ReportRepository reportRepository,
                       EmailVerificationService emailVerification,
                       AccountLinks accountLinks,
                       Legal legal) {
        this.legal = legal;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.fileStorage = fileStorage;
        this.reportRepository = reportRepository;
        this.emailVerification = emailVerification;
        this.accountLinks = accountLinks;
    }

    /** Ile zasadnych zgloszen ma to konto. */
    private long resolvedReports(User user) {
        return reportRepository.countByReportedIdAndStatus(user.getId(), ReportStatus.RESOLVED);
    }

    /** Liczby zasadnych zgloszen dla calej strony - jednym zapytaniem. */
    private Map<Long, Long> resolvedReportsFor(List<Long> userIds) {
        if (userIds.isEmpty()) {
            // IN () z pusta lista to blad skladni SQL - ta sama pulapka
            // co przy circleIds w tablicy
            return Map.of();
        }
        return reportRepository.countByStatusForUsers(ReportStatus.RESOLVED, userIds).stream()
            .collect(Collectors.toMap(CountByUser::userId, CountByUser::count));
    }

    /**
     * Zaklada nowe konto. Gdy serwer wysyla poczte, na podany adres idzie
     * link - i dopiero po jego kliknieciu da sie zalogowac.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = EmailAddresses.normalize(request.email());
        if (userRepository.existsByUsername(request.username())) {
            throw DuplicateResourceException.username(request.username());
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException.email(email);
        }

        // Haslo NIGDY nie trafia do bazy jawnym tekstem - zapisujemy hash BCrypt.
        String hash = passwordEncoder.encode(request.password());

        User created = new User(request.username(), email, hash);
        // Zgoda na regulamin: wersja, ktora osoba miala przed oczami, i moment - dowod na wypadek sporu
        created.acceptTerms(legal.version(), LocalDateTime.now());
        User stored = userRepository.save(created);
        emailVerification.afterRegistration(stored);

        return userMapper.toResponse(stored);
    }

    /**
     * Znajduje uzytkownika po loginie. orElseThrow wprost z wykladu 3 (slajd 66) - realizuje
     * wymaganie nr 11 (wyjatek przy braku elementu w bazie).
     */
    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        return userMapper.toResponse(user);
    }

    /** Podglad konta przez administratora - z rola, bo admin moze ja zmieniac. */
    @Transactional(readOnly = true)
    public AdminUserResponse getByIdForAdmin(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        return userMapper.toAdminResponse(user, resolvedReports(user));
    }

    /** Zmiana loginu i adresu e-mail we wlasnym profilu. */
    @Transactional
    public UserResponse updateProfile(String currentUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new NoSuchElementFoundException("user", currentUsername));

        if (!user.getUsername().equals(request.username())
            && userRepository.existsByUsername(request.username())) {
            throw DuplicateResourceException.username(request.username());
        }
        String email = EmailAddresses.normalize(request.email());
        boolean nowyAdres = !user.getEmail().equalsIgnoreCase(email);
        if (nowyAdres && userRepository.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException.email(email);
        }

        if (nowyAdres) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw InvalidCurrentPasswordException.missing();
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new InvalidCurrentPasswordException();
            }
        }

        user.setUsername(request.username());
        if (nowyAdres) {
            // Z poczta nowy adres czeka na potwierdzenie; bez niej zmienia sie od razu
            emailVerification.requestChange(user, email);
        }

        // save() nie jest tu konieczne (encja jest zarzadzana w transakcji),
        // ale zapisane wprost latwiej sie czyta i testuje
        return userMapper.toResponse(userRepository.save(user));
    }

    /** Jaka wersje regulaminu ma zaakceptowana ta osoba i czy to jest obecna. */
    @Transactional(readOnly = true)
    public TermsStatusResponse termsStatus(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        return new TermsStatusResponse(legal.version(), user.getTermsVersion(), user.getTermsAcceptedAt(),
            legal.version().equals(user.getTermsVersion()));
    }

    /** Akceptacja obecnej wersji - dla kont zalozonych przed regulaminem albo po jego zmianie. */
    @Transactional
    public TermsStatusResponse acceptTerms(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        user.acceptTerms(legal.version(), LocalDateTime.now());
        return termsStatus(username);
    }

    /** Zmiana wlasnego hasla. */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Inne urzadzenia wylogowane; biezaca sesja dostaje nowy znacznik w kontrolerze
        user.rotateSecurityStamp();
        userRepository.save(user);
        if (accountLinks.enabled()) {
            accountLinks.notify(user, AccountMails.Kind.PASSWORD_CHANGED, "/reset-hasla");
        }
    }

    /** "Wyloguj z innych urzadzen" - nowy znacznik; biezaca sesja dostaje go w kontrolerze. */
    @Transactional
    public void revokeOtherSessions(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        user.rotateSecurityStamp();
    }

    /** Obecny znacznik bezpieczenstwa - do zapisania w biezacej sesji. */
    @Transactional(readOnly = true)
    public String securityStampOf(String username) {
        return userRepository.securityStampOf(username).orElse("");
    }

    /** Ustawia nowe zdjecie profilowe i kasuje poprzednie. */
    @Transactional
    public UserResponse updateAvatar(String username, MultipartFile file) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        String previous = user.getAvatarFileName();
        String created = fileStorage.saveImage(file);

        user.setAvatarFileName(created);
        UserResponse response = userMapper.toResponse(userRepository.save(user));

        fileStorage.remove(previous);
        return response;
    }

    /** Usuwa zdjecie profilowe - w interfejsie wraca kolo z inicjalem. */
    @Transactional
    public UserResponse removeAvatar(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        String previous = user.getAvatarFileName();
        user.setAvatarFileName(null);

        UserResponse response = userMapper.toResponse(userRepository.save(user));
        fileStorage.remove(previous);
        return response;
    }

    /** Kasuje plik awatara konta - przy jego usuwaniu. */
    @Transactional
    public void deleteAvatarOf(User user) {
        fileStorage.remove(user.getAvatarFileName());
    }

    /** Wyszukiwanie uzytkownikow ze stronicowaniem i sortowaniem (wymagania nr 3, 5 i 8). */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> search(String fragment, Pageable pageable) {
        Page<User> found = userRepository
            .searchByUsernameOrEmail(fragment == null ? "" : fragment, pageable);

        Map<Long, Long> reports = resolvedReportsFor(
            found.getContent().stream().map(User::getId).toList());

        return found.map(user -> userMapper.toAdminResponse(
            user, reports.getOrDefault(user.getId(), 0L)));
    }

    /** Zmiana roli innego uzytkownika - operacja dostepna tylko administratorowi. */
    @Transactional
    public AdminUserResponse changeRole(String adminUsername, Long id, ChangeRoleRequest request) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        /*
         * Login admina bierzemy z sesji, a nie z zapytania - dzieki temu nie da sie obejsc tej
         * blokady, podajac w JSON-ie cudzy login.
         */
        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownRole();
        }

        target.setRole(request.role());

        User saved = userRepository.save(target);
        return userMapper.toAdminResponse(saved, resolvedReports(saved));
    }
}
