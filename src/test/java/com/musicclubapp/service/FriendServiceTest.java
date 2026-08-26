package com.musicclubapp.service;

import com.musicclubapp.dto.FriendshipStatus;
import com.musicclubapp.entity.FriendRequest;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testy jednostkowe znajomych - wymaganie nr 13.
 *
 * <p>Najwazniejsze sprawdzenia to te pilnujace, ZE ZNAJOMOSC JEST OBUSTRONNA
 * i ze nikt nie moze przyjac cudzego zaproszenia.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FriendService - zapraszanie i lista znajomych")
class FriendServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendRequestRepository requestRepository;

    @InjectMocks
    private FriendService friendService;

    private User ala() {
        return new User("ala", "ala@example.com", "hash");
    }

    private User bob() {
        return new User("bob", "bob@example.com", "hash");
    }

    /** Oboje istnieja, nie sa znajomymi i nie ma miedzy nimi zadnych zaproszen. */
    private void obcyDlaSiebie(User a, User b) {
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(a));
        given(userRepository.findByUsername("bob")).willReturn(Optional.of(b));
        given(userRepository.czySaZnajomymi("ala", "bob")).willReturn(false);
        given(requestRepository.znajdz("ala", "bob")).willReturn(Optional.empty());
        given(requestRepository.znajdz("bob", "ala")).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("zaproszenie tworzy wpis oczekujacy, a nie od razu znajomosc")
    void zaproszenieTworzyWpis() {
        User a = ala();
        User b = bob();
        obcyDlaSiebie(a, b);

        boolean odRazu = friendService.zapros("ala", "bob");

        assertThat(odRazu).isFalse();
        verify(requestRepository).save(any(FriendRequest.class));
        // dopoki bob nie przyjmie, nikt nikogo nie ma w znajomych
        assertThat(a.getFriends()).isEmpty();
        assertThat(b.getFriends()).isEmpty();
    }

    @Test
    @DisplayName("gdy obie osoby zaprosza sie nawzajem, znajomosc powstaje OD RAZU")
    void wzajemneZaproszenieLaczyOdRazu() {
        User a = ala();
        User b = bob();
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(a));
        given(userRepository.findByUsername("bob")).willReturn(Optional.of(b));
        given(userRepository.czySaZnajomymi("ala", "bob")).willReturn(false);
        given(requestRepository.znajdz("ala", "bob")).willReturn(Optional.empty());

        // bob juz wczesniej zaprosil ale
        FriendRequest odBoba = new FriendRequest(b, a);
        given(requestRepository.znajdz("bob", "ala")).willReturn(Optional.of(odBoba));

        boolean odRazu = friendService.zapros("ala", "bob");

        assertThat(odRazu).isTrue();
        assertThat(a.getFriends()).contains(b);
        assertThat(b.getFriends()).contains(a);
        // zuzyte zaproszenie znika, zamiast zostawac jako drugie lustrzane
        verify(requestRepository).delete(odBoba);
        verify(requestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("nie da sie zaprosic samego siebie")
    void zaproszenieDoSiebieOdrzucone() {
        assertThatThrownBy(() -> friendService.zapros("ala", "ala"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(requestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("nie da sie zaprosic kogos, kto juz jest znajomym")
    void ponowneZaproszenieZnajomegoOdrzucone() {
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(ala()));
        given(userRepository.findByUsername("bob")).willReturn(Optional.of(bob()));
        given(userRepository.czySaZnajomymi("ala", "bob")).willReturn(true);

        assertThatThrownBy(() -> friendService.zapros("ala", "bob"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(requestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("nie da sie wyslac drugiego zaproszenia do tej samej osoby")
    void drugieZaproszenieOdrzucone() {
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(ala()));
        given(userRepository.findByUsername("bob")).willReturn(Optional.of(bob()));
        given(userRepository.czySaZnajomymi("ala", "bob")).willReturn(false);
        given(requestRepository.znajdz("ala", "bob"))
            .willReturn(Optional.of(new FriendRequest(ala(), bob())));

        assertThatThrownBy(() -> friendService.zapros("ala", "bob"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(requestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("przyjecie zaproszenia dodaje znajomosc PO OBU stronach")
    void przyjecieDzialaObustronnie() {
        User a = ala();
        User b = bob();
        FriendRequest zaproszenie = new FriendRequest(a, b);   // ala -> bob
        given(requestRepository.findById(7L)).willReturn(Optional.of(zaproszenie));

        friendService.przyjmij(7L, "bob");

        assertThat(a.getFriends()).contains(b);
        assertThat(b.getFriends()).contains(a);
        verify(requestRepository).delete(zaproszenie);
    }

    @Test
    @DisplayName("NIE mozna przyjac zaproszenia skierowanego do kogos innego")
    void cudzeZaproszenieNieDoPrzyjecia() {
        User a = ala();
        User b = bob();
        FriendRequest zaproszenie = new FriendRequest(a, b);   // ala -> bob

        given(requestRepository.findById(7L)).willReturn(Optional.of(zaproszenie));

        // probuje przyjac ktos trzeci
        assertThatThrownBy(() -> friendService.przyjmij(7L, "czarek"))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(a.getFriends()).isEmpty();
        assertThat(b.getFriends()).isEmpty();
        verify(requestRepository, never()).delete(any(FriendRequest.class));
    }

    @Test
    @DisplayName("nadawca moze anulowac swoje zaproszenie")
    void nadawcaAnulujeSwoje() {
        FriendRequest zaproszenie = new FriendRequest(ala(), bob());
        given(requestRepository.findById(7L)).willReturn(Optional.of(zaproszenie));

        friendService.odrzucLubAnuluj(7L, "ala");

        verify(requestRepository).delete(zaproszenie);
    }

    @Test
    @DisplayName("osoba postronna nie skasuje cudzego zaproszenia")
    void postronnyNieKasuje() {
        FriendRequest zaproszenie = new FriendRequest(ala(), bob());
        given(requestRepository.findById(7L)).willReturn(Optional.of(zaproszenie));

        assertThatThrownBy(() -> friendService.odrzucLubAnuluj(7L, "czarek"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(requestRepository, never()).delete(any(FriendRequest.class));
    }

    @Test
    @DisplayName("usuniecie znajomego dziala po obu stronach")
    void usuniecieObustronne() {
        User a = ala();
        User b = bob();
        a.dodajZnajomego(b);
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(a));
        given(userRepository.findByUsername("bob")).willReturn(Optional.of(b));

        friendService.usunZnajomego("ala", "bob");

        assertThat(a.getFriends()).isEmpty();
        assertThat(b.getFriends()).isEmpty();
    }

    @Test
    @DisplayName("status: wlasny profil to SELF")
    void statusWlasnyProfil() {
        assertThat(friendService.status("ala", "ala")).isEqualTo(FriendshipStatus.SELF);
    }

    @Test
    @DisplayName("status rozroznia zaproszenie WYSLANE od OTRZYMANEGO")
    void statusRozrozniaKierunek() {
        given(userRepository.czySaZnajomymi("ala", "bob")).willReturn(false);
        given(requestRepository.znajdz("ala", "bob"))
            .willReturn(Optional.of(new FriendRequest(ala(), bob())));

        assertThat(friendService.status("ala", "bob"))
            .isEqualTo(FriendshipStatus.REQUEST_SENT);

        // ten sam uklad widziany oczami boba
        given(userRepository.czySaZnajomymi("bob", "ala")).willReturn(false);
        given(requestRepository.znajdz("bob", "ala")).willReturn(Optional.empty());

        assertThat(friendService.status("bob", "ala"))
            .isEqualTo(FriendshipStatus.REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("zaproszenie nieistniejacego uzytkownika konczy sie bledem 'nie znaleziono'")
    void nieistniejacyUzytkownik() {
        given(userRepository.findByUsername("ala")).willReturn(Optional.of(ala()));
        given(userRepository.findByUsername("duch")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.zapros("ala", "duch"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }
}
