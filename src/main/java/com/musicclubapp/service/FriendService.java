package com.musicclubapp.service;

import com.musicclubapp.dto.FriendCardResponse;
import com.musicclubapp.dto.FriendRequestResponse;
import com.musicclubapp.dto.FriendshipStatus;
import com.musicclubapp.dto.PendingRequestsResponse;
import com.musicclubapp.entity.FriendRequest;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.FriendRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
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
    public boolean zapros(String login, String kogoLogin) {
        if (login.equals(kogoLogin)) {
            throw OperationNotAllowedException.zaproszenieDoSiebie();
        }

        User ja = user(login);
        User on = user(kogoLogin);

        if (userRepository.czySaZnajomymi(login, kogoLogin)) {
            throw OperationNotAllowedException.juzZnajomi();
        }
        if (requestRepository.znajdz(login, kogoLogin).isPresent()) {
            throw OperationNotAllowedException.zaproszenieJuzWyslane();
        }

        // On zaprosil mnie wczesniej -> po prostu przyjmujemy tamto zaproszenie
        var odNiego = requestRepository.znajdz(kogoLogin, login);
        if (odNiego.isPresent()) {
            polacz(ja, on, odNiego.get());
            return true;
        }

        requestRepository.save(new FriendRequest(ja, on));
        return false;
    }

    /** Przyjmuje zaproszenie skierowane DO MNIE. */
    @Transactional
    public void przyjmij(Long zaproszenieId, String login) {
        FriendRequest zaproszenie = zaproszenie(zaproszenieId);

        /*
         * Kluczowe sprawdzenie: przyjac moze WYLACZNIE odbiorca. Bez tego
         * wystarczyloby zgadnac numer zaproszenia, zeby skojarzyc ze soba
         * dwie obce osoby.
         */
        if (!zaproszenie.getRecipient().getUsername().equals(login)) {
            throw OperationNotAllowedException.cudzeZaproszenie();
        }

        polacz(zaproszenie.getSender(), zaproszenie.getRecipient(), zaproszenie);
    }

    /**
     * Odrzuca zaproszenie do mnie albo anuluje moje wlasne.
     *
     * <p>Obie czynnosci to skasowanie tego samego wiersza, wiec obsluguje
     * je jedna metoda - rozni sie tylko to, po ktorej stronie stoi
     * zalogowany uzytkownik.</p>
     */
    @Transactional
    public void odrzucLubAnuluj(Long zaproszenieId, String login) {
        FriendRequest zaproszenie = zaproszenie(zaproszenieId);

        boolean mojeZaproszenie = zaproszenie.getSender().getUsername().equals(login)
            || zaproszenie.getRecipient().getUsername().equals(login);

        if (!mojeZaproszenie) {
            throw OperationNotAllowedException.cudzeZaproszenie();
        }

        requestRepository.delete(zaproszenie);
    }

    /** Usuwa znajomosc - u obu osob naraz. */
    @Transactional
    public void usunZnajomego(String login, String kogoLogin) {
        User ja = user(login);
        User on = user(kogoLogin);

        ja.usunZnajomego(on);
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
    public Page<FriendCardResponse> znajomi(String czyich, String ogladajacy, Pageable pageable) {
        // Sprawdzamy, czy taka osoba w ogole istnieje - inaczej pusta lista
        // wygladalaby jak "ten uzytkownik nie ma znajomych"
        user(czyich);

        return userRepository.znajomiPosortowani(czyich, ogladajacy, pageable)
            .map(this::naKafelek);
    }

    /** Zaproszenia oczekujace - przychodzace i wyslane naraz. */
    @Transactional(readOnly = true)
    public PendingRequestsResponse oczekujace(String login) {
        List<FriendRequestResponse> przychodzace = requestRepository.przychodzace(login).stream()
            .map(z -> naOdpowiedz(z, z.getSender()))
            .toList();

        List<FriendRequestResponse> wyslane = requestRepository.wyslane(login).stream()
            .map(z -> naOdpowiedz(z, z.getRecipient()))
            .toList();

        return new PendingRequestsResponse(przychodzace, wyslane);
    }

    /**
     * W jakiej relacji jest ogladajacy z dana osoba.
     *
     * <p>Wyliczamy to na serwerze, zeby frontend wiedzial, ktory przycisk
     * narysowac ("Zapros", "Przyjmij", "Usun ze znajomych"), zamiast skladac
     * to sobie z kilku osobnych zapytan.</p>
     */
    @Transactional(readOnly = true)
    public FriendshipStatus status(String ogladajacy, String kogo) {
        if (ogladajacy.equals(kogo)) {
            return FriendshipStatus.SELF;
        }
        if (userRepository.czySaZnajomymi(ogladajacy, kogo)) {
            return FriendshipStatus.FRIENDS;
        }
        if (requestRepository.znajdz(ogladajacy, kogo).isPresent()) {
            return FriendshipStatus.REQUEST_SENT;
        }
        if (requestRepository.znajdz(kogo, ogladajacy).isPresent()) {
            return FriendshipStatus.REQUEST_RECEIVED;
        }
        return FriendshipStatus.NONE;
    }

    /** Ile zaproszen czeka na moja odpowiedz - liczba przy pozycji w menu. */
    @Transactional(readOnly = true)
    public long ileOczekujacych(String login) {
        return requestRepository.countByRecipientUsername(login);
    }

    // ----------------------------------------------------------------------

    /** Laczy dwie osoby w znajomych i kasuje zuzyte zaproszenie. */
    private void polacz(User a, User b, FriendRequest zaproszenie) {
        a.dodajZnajomego(b);
        userRepository.save(a);
        userRepository.save(b);

        /*
         * Zaproszenie znika po przyjeciu - tabela friend_requests trzyma
         * WYLACZNIE oczekujace. Dzieki temu zadne zapytanie o zaproszenia
         * nie musi pamietac o filtrowaniu po statusie.
         */
        requestRepository.delete(zaproszenie);
    }

    private FriendCardResponse naKafelek(FriendRow wiersz) {
        return new FriendCardResponse(
            wiersz.getUsername(),
            adresAvatara(wiersz.getAvatarFileName()),
            wiersz.getWspolniZnajomi());
    }

    private FriendRequestResponse naOdpowiedz(FriendRequest zaproszenie, User drugaStrona) {
        return new FriendRequestResponse(
            zaproszenie.getId(),
            drugaStrona.getUsername(),
            adresAvatara(drugaStrona.getAvatarFileName()),
            zaproszenie.getCreatedAt());
    }

    /** Baza trzyma nazwe pliku, na zewnatrz wychodzi gotowy adres - jak przy postach. */
    private String adresAvatara(String nazwaPliku) {
        return nazwaPliku == null ? null : PostMapper.SCIEZKA_PLIKOW + nazwaPliku;
    }

    private User user(String login) {
        return userRepository.findByUsername(login)
            .orElseThrow(() -> new NoSuchElementFoundException("user", login));
    }

    private FriendRequest zaproszenie(Long id) {
        return requestRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("friendRequest", id));
    }
}
