package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.MessagingBanRequest;
import com.musicclubapp.dto.PostingBanRequest;
import com.musicclubapp.dto.RelatedAccountResponse;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Report;
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

    /*
     * Kasowanie posta idzie przez PostService, a nie wprost przez repozytorium:
     * tam siedzi usuwanie zdjec z dysku i sprawdzenie uprawnien. Powtorzenie
     * tego tutaj konczyloby sie plikami zostawionymi na serwerze po poscie,
     * ktorego juz nie ma.
     */
    private final PostService postService;
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
                                 ReportRepository reportRepository,
                                 PostService postService) {
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
        this.postService = postService;
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
     * Zamyka zgloszenie i <b>od razu</b> wykonuje decyzje administratora.
     *
     * <p><b>Dlaczego to jest tutaj, a nie w {@code ReportService}.</b> Kary
     * mieszkaja w tej klasie, a {@code ReportService} nie moze po nie siegnac:
     * ta klasa juz od niego zalezy (kasujac konto, kasuje tez jego zgloszenia),
     * wiec zaleznosc w druga strone zamykalaby kolo i Spring nie wstalby
     * w ogole. Kolo rozcina sie tak, ze warstwa "wiedzaca wiecej" wola te
     * "wiedzaca mniej" - a to moderacja wie o zgloszeniach, nie odwrotnie.</p>
     *
     * <p><b>Jedna transakcja na decyzje i kare.</b> Gdyby to byly dwa osobne
     * wywolania z przegladarki, awaria miedzy nimi zostawialaby stan, ktorego
     * nie da sie sensownie opisac: zgloszenie zamkniete z notatka "konto
     * usuniete", a konto na miejscu. Tutaj albo dzieje sie jedno i drugie,
     * albo nic.</p>
     *
     * <p><b>Kolejnosc: najpierw zamkniecie, potem kara.</b> Zamkniecie
     * sprawdza, czy ktos inny nie zdazyl juz podjac decyzji - i jesli zdazyl,
     * przerywa. Przy odwrotnej kolejnosci kara zdazylaby sie wykonac
     * <i>drugi raz</i>, zanim wyszloby na jaw, ze sprawa jest juz zamknieta.</p>
     */
    @Transactional
    public ReportResponse resolveReport(String adminUsername, Long id,
                                        ResolveReportRequest request) {

        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("report", id));

        ModerationAction action = request.actionOrNone();

        /*
         * Kasowanie posta ma sens tylko przy zgloszeniu, ktore posta dotyczy.
         * Sprawdzamy to PRZED zamknieciem sprawy: inaczej zgloszenie bylo by
         * juz zamkniete, gdy okaze sie, ze zadanej kary nie da sie wykonac,
         * a zamkniecia nie da sie cofnac.
         */
        if (action == ModerationAction.DELETE_POST && report.getPost() == null) {
            throw OperationNotAllowedException.reportHasNoPost();
        }

        ReportResponse closed = reports.resolve(adminUsername, id, request);

        User target = report.getReported();
        switch (action) {
            case DELETE_POST -> {
                Long postId = report.getPost().getId();

                /*
                 * Najpierw odpinamy post od zgloszen, dopiero potem kasujemy.
                 * Odwrotna kolejnosc konczy sie odmowa bazy (klucz obcy
                 * z tabeli zgloszen) - i konczyla sie, zanim to powstalo.
                 * Odpiac trzeba WSZYSTKIE zgloszenia, nie tylko to rozpatrywane:
                 * ten sam post mogl zglosic ktos jeszcze.
                 */
                reportRepository.detachPost(postId);

                postService.delete(postId, adminUsername);
                log.info("Administrator {} skasowal post {} przy zgloszeniu {}",
                    adminUsername, postId, id);
            }
            case BAN_POSTING -> setPostingBan(adminUsername, target.getId(),
                new PostingBanRequest(request.hours(), request.forever()));
            case BAN_MESSAGING -> setMessagingBan(adminUsername, target.getId(),
                new MessagingBanRequest(request.hours(), request.forever()));
            case DELETE_ACCOUNT -> deleteUser(adminUsername, target.getId());
            case NONE -> log.info("Administrator {} zamknal zgloszenie {} bez dzialan",
                adminUsername, id);
        }

        return closed;
    }

    /**
     * Termin konca kary dla obu rodzajow zakazu - jedno miejsce na te regule.
     *
     * <p>Trzy mozliwosci, w tej kolejnosci:</p>
     * <ol>
     *   <li>{@code forever} - zakaz bezterminowy ({@link User#FOREVER}),</li>
     *   <li>podana liczba godzin - termin liczony od <b>zegara serwera</b>,</li>
     *   <li>brak obu - {@code null}, czyli zdjecie zakazu.</li>
     * </ol>
     *
     * <p><b>Kolejnosc ma znaczenie.</b> Zakaz bezterminowy przychodzi bez
     * liczby godzin, wiec gdyby najpierw sprawdzac {@code hours == null},
     * "na zawsze" zdejmowaloby kare zamiast ja nakladac - czyli robiloby
     * doslownie odwrotnosc tego, co administrator kliknal.</p>
     *
     * <p>Termin liczymy od zegara serwera, a nie przyjmujemy gotowego
     * z przegladarki: data z przeszlosci byla by kara konczaca sie, zanim
     * sie zaczela.</p>
     */
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

        LocalDateTime until = banUntil(payload.hours(), payload.forever());
        target.setPostingBannedUntil(until);
        log.info("Administrator {} ustawil zakaz publikowania konta {}: {}",
            adminUsername, target.getUsername(), describeBan(until));

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

        LocalDateTime until = banUntil(payload.hours(), payload.forever());
        target.setMessagingBannedUntil(until);
        log.info("Administrator {} ustawil zakaz wiadomosci konta {}: {}",
            adminUsername, target.getUsername(), describeBan(until));

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
