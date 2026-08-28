package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.MessagingBanRequest;
import com.musicclubapp.dto.PostingBanRequest;
import com.musicclubapp.dto.RelatedAccountResponse;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Uprawnienia administratora wobec kont: <b>usuwanie</b> i <b>zakaz publikowania</b>.
 *
 * <p><b>Dlaczego osobny serwis, a nie kolejne metody w {@code UserService}.</b>
 * {@code UserService} obsluguje to, co uzytkownik robi ze swoim wlasnym kontem:
 * rejestracje, zmiane danych, zmiane hasla. Moderacja to co innego - dziala
 * na <b>cudzych</b> kontach i wymaga uprawnien. Trzymanie tego osobno sprawia,
 * ze przy czytaniu widac od razu, ktore operacje sa wrazliwe.</p>
 *
 * <p><b>Kim jest wykonujacy, bierzemy z sesji, nigdy z zapytania.</b> Ta sama
 * zasada co przy zmianie roli: gdyby login administratora przychodzil w tresci
 * JSON-a, blokade "nie usuwaj sam siebie" dalo by sie obejsc, podajac cudzy
 * login.</p>
 */
@Service
public class UserModerationService {

    private static final Logger log = LoggerFactory.getLogger(UserModerationService.class);

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;
    private final FriendRequestRepository requestRepository;
    private final FavoritePlaylistRepository playlistRepository;
    private final FileStorageService fileStorage;
    private final UserMapper userMapper;
    private final NotificationService notifications;
    private final MessageService messages;
    private final ReportService reports;
    private final NetworkService network;
    private final ReportRepository reportRepository;

    public UserModerationService(UserRepository userRepository,
                                 PostRepository postRepository,
                                 ReactionRepository reactionRepository,
                                 FriendRequestRepository requestRepository,
                                 FavoritePlaylistRepository playlistRepository,
                                 FileStorageService fileStorage,
                                 UserMapper userMapper,
                                 NotificationService notifications,
                                 MessageService messages,
                                 ReportService reports,
                                 NetworkService network,
                                 ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.requestRepository = requestRepository;
        this.playlistRepository = playlistRepository;
        this.fileStorage = fileStorage;
        this.userMapper = userMapper;
        this.notifications = notifications;
        this.messages = messages;
        this.reports = reports;
        this.network = network;
        this.reportRepository = reportRepository;
    }

    /**
     * Kasuje konto razem ze wszystkim, co po nim zostalo.
     *
     * <p><b>Samo {@code delete(user)} tu nie wystarczy</b> i nie jest to
     * drobiazg techniczny. Na koncie wisza wiersze w kilku tabelach, a czesc
     * z nich wskazuje na nie z drugiej strony - Hibernate sam ich nie ruszy
     * i baza odmowi usuniecia z powodu klucza obcego. Kolejnosc ponizej jest
     * wiec obowiazkowa: najpierw wszystko, co wskazuje na uzytkownika, dopiero
     * na koncu on sam.</p>
     *
     * <p>Kasujemy tez <b>pliki z dysku</b>: awatar i zdjecia z postow. Konto
     * usuniete z bazy, ale z fotografiami leżacymi dalej na serwerze, to
     * usuniecie tylko na niby.</p>
     */
    @Transactional
    public void deleteUser(String adminUsername, Long id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        /*
         * 1. Powiadomienia w OBIE strony - te, ktore dostala, i te, ktore
         * wywolala u innych. Musza pojsc pierwsze, bo wskazuja kluczami
         * obcymi zarowno na konto, jak i na posty kasowane nizej.
         */
        notifications.userDeleted(target.getId());

        // 2. Reakcje tej osoby pod CUDZYMI postami - te posty maja zostac
        reactionRepository.deleteByUserId(target.getId());

        // 3. Wlasne posty; kaskada zabiera ich zdjecia i cudze reakcje pod nimi
        List<Post> posts = postRepository.findByAuthorId(target.getId());
        for (Post post : posts) {
            for (PostImage image : post.getImages()) {
                fileStorage.remove(image.getFileName());
            }
        }
        postRepository.deleteAll(posts);

        // 4. Zaproszenia w obie strony
        requestRepository.deleteBySenderIdOrRecipientId(target.getId(), target.getId());

        /*
         * 5. Wiadomosci z czatu - te wyslane i te otrzymane.
         *
         * Kasujemy je razem z kontem, choc druga strona rozmowy jeszcze
         * istnieje. To swiadoma decyzja, a nie uproszczenie: wiadomosc bez
         * nadawcy nie ma jak sie wyswietlic (nie ma loginu ani awatara),
         * a zostawienie jej "osieroconej" wymagaloby trzymania kopii danych
         * usunietego konta - czyli nieusuwania go do konca.
         */
        messages.deleteAllOf(target.getId());

        /*
         * 6. Zgloszenia - te zlozone przez to konto i te na nie. Odpina przy
         * okazji jego posty od CUDZYCH zgloszen, bo posty ida do kasacji
         * nizej, a zgloszenie wskazuje na nie kluczem obcym.
         */
        reports.deleteAllOf(target.getId());

        /*
         * 7. Historia adresow sieciowych. Kasujemy razem z kontem, choc kusi,
         * zeby zostawic ja "na wszelki wypadek" - wiersze bez konta nadal
         * mowilyby, ze z danego adresu ktos byl. Usuniete konto ma zniknac,
         * a nie zostawic po sobie slad w innej tabeli.
         */
        network.forgetUser(target.getId());

        /*
         * 8. Znajomosci. Wiersz w user_friends powstaje w OBIE strony, a
         * Hibernate przy kasowaniu encji sprzata tylko te, w ktorych ta osoba
         * jest wlascicielem relacji. Drugiej polowy trzeba pozbyc sie recznie -
         * inaczej u znajomych zostalby wpis wskazujacy na nieistniejace konto.
         */
        userRepository.removeFriendshipsWith(target.getId());
        target.getFriends().clear();

        // 9. Ulubieni - tu strona wlascicielska wystarczy
        target.getFavoriteArtists().clear();
        target.getFavoriteTracks().clear();

        /*
         * 10. Gablotka playlist. Wprost, a nie kaskada: wiersze wskazuja na
         * konto kluczem obcym, a encja User nic o nich nie wie - gdyby
         * zostaly, baza odmowilaby skasowania konta.
         */
        playlistRepository.deleteByOwnerId(target.getId());

        if (target.getAvatarFileName() != null) {
            fileStorage.remove(target.getAvatarFileName());
        }

        userRepository.delete(target);
        log.info("Administrator {} usunal konto {} (postow: {})",
            adminUsername, target.getUsername(), posts.size());
    }

    /**
     * Naklada albo zdejmuje zakaz publikowania.
     *
     * <p>Pusta liczba godzin znaczy "zdejmij zakaz" - to jedna operacja
     * zamiast dwoch endpointow, bo w panelu to jeden przelacznik.</p>
     *
     * <p>Termin liczymy od <b>zegara serwera</b>. Gdyby przychodzil gotowy
     * z przegladarki, dalo by sie przyslac date z przeszlosci, czyli kare
     * konczaca sie zanim sie zaczela.</p>
     */
    @Transactional
    public AdminUserResponse setPostingBan(String adminUsername, Long id, PostingBanRequest payload) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        /*
         * Blokada samego siebie nie jest grozna tak jak usuniecie konta, ale
         * i tak nie ma sensu: administrator odebralby sobie mozliwosc pisania,
         * zachowujac mozliwosc jej zdjecia. To zwykla pomylka, a nie decyzja.
         */
        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        if (payload.hours() == null) {
            target.setPostingBannedUntil(null);
            log.info("Administrator {} zdjal zakaz publikowania z konta {}",
                adminUsername, target.getUsername());
        } else {
            LocalDateTime until = LocalDateTime.now().plusHours(payload.hours());
            target.setPostingBannedUntil(until);
            log.info("Administrator {} nalozyl na konto {} zakaz publikowania do {}",
                adminUsername, target.getUsername(), until);
        }

        return toResponse(userRepository.save(target));
    }

    /**
     * Naklada albo zdejmuje <b>zakaz wysylania wiadomosci</b>.
     *
     * <p>Osobna kara od zakazu publikowania - patrz komentarz przy
     * {@code User.messagingBannedUntil}. Ta sama mechanika: godziny zamiast
     * daty, termin liczony od zegara SERWERA, pusta wartosc zdejmuje zakaz.</p>
     *
     * <p>Blokada "sam sobie" jest tu z tego samego powodu co przy publikowaniu:
     * to zwykla pomylka, a nie decyzja.</p>
     */
    @Transactional
    public AdminUserResponse setMessagingBan(String adminUsername, Long id,
                                             MessagingBanRequest payload) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownAccount();
        }

        if (payload.hours() == null) {
            target.setMessagingBannedUntil(null);
            log.info("Administrator {} zdjal zakaz wiadomosci z konta {}",
                adminUsername, target.getUsername());
        } else {
            LocalDateTime until = LocalDateTime.now().plusHours(payload.hours());
            target.setMessagingBannedUntil(until);
            log.info("Administrator {} nalozyl na konto {} zakaz wiadomosci do {}",
                adminUsername, target.getUsername(), until);
        }

        return toResponse(userRepository.save(target));
    }

    /**
     * Konta logujace sie z tych samych adresow co wskazane.
     *
     * <p><b>To jest poszlaka, nie dowod</b> - pod jednym adresem siedzi cala
     * rodzina, akademik albo tysiace klientow operatora komorkowego. Dlatego
     * aplikacja nigdzie nie blokuje nikogo automatycznie na tej podstawie,
     * a odpowiedz zawiera takze liczbe logowan i date ostatniego: dwa konta
     * z jednym wejsciem sprzed pol roku znacza co innego niz dwa uzywane
     * naprzemiennie codziennie.</p>
     */
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
