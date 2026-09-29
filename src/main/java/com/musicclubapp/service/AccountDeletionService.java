package com.musicclubapp.service;

import com.musicclubapp.entity.User;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Co i w jakiej KOLEJNOSCI usunac, zeby baza nie odmowila.
 *
 * <p>Kasowanie konta ma dwie drogi - administrator w panelu i sam wlasciciel
 * w ustawieniach - a kolejnosc krokow jest w obu ta sama. Trzymanie jej
 * w jednym miejscu jest tu warunkiem poprawnosci, a nie porzadkiem: pominiety
 * albo przestawiony krok konczy sie bledem klucza obcego u uzytkownika.</p>
 *
 * <p>Ta klasa nie kasuje niczego sama - prosi po kolei moduly, ktore sa
 * wlascicielami swoich tabel.</p>
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final NotificationService notifications;
    private final ReactionService reactions;
    private final ReportService reports;
    private final PostService posts;
    private final FriendService friends;
    private final MessageService messages;
    private final NetworkService network;
    private final PlaylistService playlists;
    private final EventParticipationService eventParticipations;
    private final EmailVerificationService emailVerification;
    private final BlockService blocks;
    private final UserService users;

    public AccountDeletionService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  NotificationService notifications,
                                  ReactionService reactions,
                                  ReportService reports,
                                  PostService posts,
                                  FriendService friends,
                                  MessageService messages,
                                  NetworkService network,
                                  PlaylistService playlists,
                                  EventParticipationService eventParticipations,
                                  EmailVerificationService emailVerification,
                                  BlockService blocks,
                                  UserService users) {
        this.blocks = blocks;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.reactions = reactions;
        this.reports = reports;
        this.posts = posts;
        this.friends = friends;
        this.messages = messages;
        this.network = network;
        this.playlists = playlists;
        this.eventParticipations = eventParticipations;
        this.emailVerification = emailVerification;
        this.users = users;
    }

    /* ------------------------------------------------------------------ */
    /*  Na zadanie wlasciciela konta                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Kasuje wlasne konto po potwierdzeniu haslem.
     *
     * <p>Haslo nie jest formalnoscia: sesja moze byc otwarta na cudzym
     * komputerze, a tej operacji nie da sie cofnac.</p>
     */
    @Transactional
    public void deleteOwnAccount(String username, String password) {
        User user = requireWithPassword(username, password);

        erase(user);
        log.info("Uzytkownik {} skasowal wlasne konto", username);
    }

    /**
     * Kasuje wszystkie wlasne posty, zostawiajac konto.
     *
     * @return ile postow zniknelo
     */
    @Transactional
    public int deleteOwnPosts(String username, String password) {
        User user = requireWithPassword(username, password);

        int ile = erasePostsOf(user);
        log.info("Uzytkownik {} skasowal wszystkie swoje posty ({})", username, ile);
        return ile;
    }

    private User requireWithPassword(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        return user;
    }

    /* ------------------------------------------------------------------ */
    /*  Kolejnosc krokow                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Kasuje konto razem ze wszystkim, co po nim zostalo.
     *
     * <p>Ta metoda jest LISTA KROKOW, a nie ich wykonaniem - kazdy krok to
     * jedno zdanie "module X, posprzataj po tym koncie". Dzieki temu nowa
     * funkcja z wlasna tabela dopisuje tu jedna linijke, a sprzatanie pisze
     * przy sobie.</p>
     */
    @Transactional
    public void erase(User target) {
        Long id = target.getId();

        // 1. Powiadomienia - wskazuja i na konto, i na posty kasowane nizej
        notifications.deleteAllOf(id);

        // 2. Reakcje tej osoby pod CUDZYMI postami - te posty maja zostac
        reactions.deleteAllOf(id);

        // 3. Zgloszenia zlozone przez to konto i te na nie
        reports.deleteAllOf(id);

        /*
         * 4. Wlasne posty razem ze zdjeciami z dysku. Wprost, a nie przez
         * erasePostsOf: powiadomienia i zgloszenia tego konta znikly juz
         * w krokach 1 i 3, a kazde dodatkowe zapytanie modyfikujace czysci
         * kontekst Hibernate i odpina encje, ktorej uzywamy jeszcze nizej.
         */
        posts.deleteAllOf(id);

        // 5. Zaproszenia i znajomosci
        friends.deleteAllOf(target);

        // 6. Wiadomosci z czatu - wyslane i otrzymane
        messages.deleteAllOf(id);

        // 7. Historia adresow sieciowych
        network.deleteAllOf(id);

        // 8. Ulubieni - kolekcje samej encji, wiec zostaja tutaj
        target.getFavoriteArtists().clear();
        target.getFavoriteTracks().clear();

        // 9. Gablotka playlist
        playlists.deleteAllOf(id);

        // 10. Zapisy na wydarzenia - inaczej zostalby "ktos" na liscie uczestnikow
        eventParticipations.deleteAllOf(id);

        // 11. Linki potwierdzajace adres e-mail
        emailVerification.deleteAllOf(id);

        // 12. Blokady - zalozone przez to konto i na nie
        blocks.deleteAllOf(id);

        // 13. Zdjecie profilowe z dysku
        users.deleteAvatarOf(target);

        userRepository.delete(target);
    }

    /**
     * Kasuje posty jednej osoby razem z tym, co na nie wskazuje.
     *
     * <p>Kolejnosc jest obowiazkowa: powiadomienia i zgloszenia wskazuja na
     * posty kluczem obcym, wiec dopoki wskazuja, baza postow nie odda.
     * Zgloszenia <b>odpinamy</b>, a nie kasujemy - sa historia konta
     * zglaszajacego, a tresc posta zostaje w migawce dowodow.</p>
     */
    @Transactional
    public int erasePostsOf(User owner) {
        notifications.postsOfAuthorDeleted(owner.getId());
        reports.detachPostsOf(owner.getId());
        return posts.deleteAllOf(owner.getId());
    }
}
