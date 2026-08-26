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

/**
 * Znajomi: zapraszanie, przyjmowanie i lista.
 *
 * <p><b>Realizuje wymaganie nr 7</b> - relacja {@code @ManyToMany}
 * ({@code User.friends}).</p>
 *
 * <p><b>Znajomosc jest obustronna.</b> Nie ma czegos takiego jak
 * "obserwowanie" - po przyjeciu zaproszenia obie osoby widza sie nawzajem
 * na swoich listach. Dlatego encja {@link FriendRequest} istnieje tylko
 * do momentu odpowiedzi, a potem znika.</p>
 */
@Service
public class FriendService {

    private final UserRepository userRepository;
    private final FriendRequestRepository requestRepository;

    public FriendService(UserRepository userRepository,
                         FriendRequestRepository requestRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
    }

    /**
     * Wysyla zaproszenie do znajomych.
     *
     * <p><b>Przypadek szczegolny: obie osoby zaprosily sie nawzajem.</b>
     * Jesli druga strona juz nas zaprosila, nie tworzymy drugiego zaproszenia
     * "w druga strone" - od razu przyjmujemy tamto. Obie osoby wyrazily
     * przeciez zgode, wiec czekanie na dodatkowe klikniecie byloby
     * bez sensu, a w bazie zostalyby dwa lustrzane zaproszenia, ktore
     * trzeba by potem sprzatac.</p>
     *
     * @return {@code true}, gdy od razu powstala znajomosc (przypadek wyzej)
     */
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

        requestRepository.save(new FriendRequest(ja, on));
        return false;
    }

    /** Przyjmuje zaproszenie skierowane DO MNIE. */
    @Transactional
    public void accept(Long invitationId, String username) {
        FriendRequest invitation = invitation(invitationId);

        /*
         * Kluczowe sprawdzenie: przyjac moze WYLACZNIE odbiorca. Bez tego
         * wystarczyloby zgadnac numer zaproszenia, zeby skojarzyc ze soba
         * dwie obce osoby.
         */
        if (!invitation.getRecipient().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesInvitation();
        }

        merge(invitation.getSender(), invitation.getRecipient(), invitation);
    }

    /**
     * Odrzuca zaproszenie do mnie albo anuluje moje wlasne.
     *
     * <p>Obie czynnosci to skasowanie tego samego wiersza, wiec obsluguje
     * je jedna metoda - rozni sie tylko to, po ktorej stronie stoi
     * zalogowany uzytkownik.</p>
     */
    @Transactional
    public void rejectOrCancel(Long invitationId, String username) {
        FriendRequest invitation = invitation(invitationId);

        boolean myInvitation = invitation.getSender().getUsername().equals(username)
            || invitation.getRecipient().getUsername().equals(username);

        if (!myInvitation) {
            throw OperationNotAllowedException.someoneElsesInvitation();
        }

        requestRepository.delete(invitation);
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

    /**
     * Lista znajomych danej osoby, od najbardziej powiazanych z ogladajacym.
     *
     * <p>Stronicowana (wymagania nr 3 i 5) - pasek na profilu pobiera kolejne
     * strony po klknieciu strzalki, zamiast ciagnac wszystkich naraz.</p>
     */
    @Transactional(readOnly = true)
    public Page<FriendCardResponse> friends(String whose, String viewer, Pageable pageable) {
        // Sprawdzamy, czy taka osoba w ogole istnieje - inaczej pusta lista
        // wygladalaby jak "ten uzytkownik nie ma znajomych"
        user(whose);

        return userRepository.friendsRanked(whose, viewer, pageable)
            .map(this::toCard);
    }

    /**
     * <b>Proponowani znajomi: cala spolecznosc, od najlepiej dopasowanych.</b>
     *
     * <p>Nie odsiewamy nikogo poza soba samym. Zamysl jest taki, ze lista
     * pokazuje najpierw osoby o podobnym guscie, a dalej po prostu pozostalych
     * uzytkownikow. Aplikacja dla kilkunastu osob, ktora po odfiltrowaniu
     * "za malo podobnych" wyswietla pusta strone, jest bezuzyteczna dokladnie
     * wtedy, kiedy najbardziej potrzeba w niej ludzi - na starcie.</p>
     *
     * <p>Znajomi tez zostaja na liscie, tylko z innym przyciskiem. Inaczej
     * osoba z najlepszym dopasowaniem znikalaby w chwili dodania jej do
     * znajomych - a to wlasnie ona najlepiej tlumaczy, po co ta lista jest.</p>
     *
     * <p>Cale liczenie robi jedno zapytanie w bazie
     * ({@code UserRepository.friendSuggestions}) - tam tez jest opis wag.</p>
     *
     * @param limit ile kart maksymalnie zwrocic (pasek i tak sie przewija)
     */
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

    /**
     * W jakiej relacji jest ogladajacy z dana osoba.
     *
     * <p>Wyliczamy to na serwerze, zeby frontend wiedzial, ktory przycisk
     * narysowac ("Zapros", "Przyjmij", "Usun ze znajomych"), zamiast skladac
     * to sobie z kilku osobnych zapytan.</p>
     */
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

    // ----------------------------------------------------------------------

    /** Laczy dwie osoby w znajomych i kasuje zuzyte zaproszenie. */
    private void merge(User a, User b, FriendRequest invitation) {
        a.addFriend(b);
        userRepository.save(a);
        userRepository.save(b);

        /*
         * Zaproszenie znika po przyjeciu - tabela friend_requests trzyma
         * WYLACZNIE oczekujace. Dzieki temu zadne zapytanie o zaproszenia
         * nie musi pamietac o filtrowaniu po statusie.
         */
        requestRepository.delete(invitation);
    }

    private FriendCardResponse toCard(FriendRow row) {
        return new FriendCardResponse(
            row.getUsername(),
            avatarUrl(row.getAvatarFileName()),
            row.getSharedFriends());
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
        return fileName == null ? null : PostMapper.SCIEZKA_PLIKOW + fileName;
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
