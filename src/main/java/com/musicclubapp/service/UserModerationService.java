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

/**
 * Uprawnienia administratora wobec KONT: usuwanie, kary i powiazania sieciowe.
 *
 * <p><b>Decyzje w zgloszeniach mieszkaja osobno</b> ({@code ReportDecisionService}).
 * Byly tu kiedys i rozdmuchaly te klase do trzynastu zaleznosci - a trafily
 * tutaj z powodu technicznego (unikniecia cyklu), a nie dlatego, ze pasuja.</p>
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
 *
 * <p><b>Nie zna ani jednego cudzego repozytorium.</b> Przy kasowaniu konta ta
 * klasa nie grzebie w tabelach postow, reakcji czy playlist - <b>prosi o to
 * moduly, ktore sa ich wlascicielami</b>. Wczesniej robila polowe tej roboty
 * sama i piec zaleznosci istnialo wylacznie po to. Rozne sa tylko powody:
 * co po koncie zostaje w danej tabeli, wie jej wlasciciel; w jakiej
 * KOLEJNOSCI o to poprosic - wie tylko ta klasa, i dlatego kolejnosc
 * zostaje tutaj, wypisana wprost w {@link #deleteUser}.</p>
 */
@Service
public class UserModerationService {

    private static final Logger log = LoggerFactory.getLogger(UserModerationService.class);

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final UserMapper userMapper;

    /*
     * Moduly, ktore sprzataja po kasowanym koncie. Kazdy z nich wie, co po
     * uzytkowniku zostaje w JEGO tabelach - ta klasa zna tylko kolejnosc,
     * w jakiej trzeba ich o to poprosic.
     */
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
     *
     * <p><b>Ta metoda jest lista krokow, a nie ich wykonaniem.</b> Kazdy krok
     * to jedno zdanie: "modul X, posprzataj po tym koncie". Co to dokladnie
     * znaczy w tabelach danego modulu - wie on sam. Dzieki temu dopisanie
     * nowej funkcji z wlasna tabela nie wymaga juz wracania tutaj: sprzatanie
     * pisze sie przy niej, a lista ponizej rosnie o jedna linijke.</p>
     */
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

        /*
         * 4. Zaproszenia i znajomosci. Ida razem, bo to jedno pojecie
         * ogladane z dwoch stron: zaproszenie to znajomosc, ktora jeszcze
         * nie doszla do skutku.
         */
        friends.deleteAllOf(target);

        /*
         * 5. Wiadomosci z czatu - te wyslane i te otrzymane.
         *
         * Kasujemy je razem z kontem, choc druga strona rozmowy jeszcze
         * istnieje. To swiadoma decyzja, a nie uproszczenie: wiadomosc bez
         * nadawcy nie ma jak sie wyswietlic (nie ma loginu ani awatara),
         * a zostawienie jej "osieroconej" wymagaloby trzymania kopii danych
         * usunietego konta - czyli nieusuwania go do konca.
         */
        messages.deleteAllOf(id);

        // 6. Zgloszenia zlozone przez to konto i te na nie
        reports.deleteAllOf(id);

        /*
         * 7. Historia adresow sieciowych. Kasujemy razem z kontem, choc kusi,
         * zeby zostawic ja "na wszelki wypadek" - wiersze bez konta nadal
         * mowilyby, ze z danego adresu ktos byl. Usuniete konto ma zniknac,
         * a nie zostawic po sobie slad w innej tabeli.
         */
        network.deleteAllOf(id);

        /*
         * 8. Ulubieni. Jedyny krok, ktory zostaje tutaj: to zwykle kolekcje
         * encji User po stronie wlascicielskiej, wiec nie ma osobnego modulu,
         * ktory bylby ich wlascicielem - jest nim samo konto.
         */
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
     * Naklada albo zdejmuje kare - <b>jedna metoda na oba rodzaje</b>.
     *
     * <p>Wczesniej byly tu dwie metody, {@code setPostingBan}
     * i {@code setMessagingBan}, ktore po znormalizowaniu nazw okazaly sie
     * identyczne co do znaku. Roznily sie wylacznie tym, ktory setter wolaja -
     * a to nie jest roznica w zachowaniu, tylko wartosc. Odkad rodzaj kary
     * jest wartoscia ({@link BanKind}), zostaje jedna sciezka.</p>
     *
     * <p>Pusta liczba godzin znaczy "zdejmij kare" - to jedna operacja zamiast
     * dwoch endpointow, bo w panelu to jeden przelacznik.</p>
     *
     * <p>Blokada samego siebie nie jest tak grozna jak usuniecie konta, ale
     * i tak nie ma sensu: administrator odebralby sobie mozliwosc pisania,
     * zachowujac mozliwosc jej zdjecia. To zwykla pomylka, a nie decyzja.</p>
     */
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
