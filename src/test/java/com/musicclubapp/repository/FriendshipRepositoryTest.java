package com.musicclubapp.repository;

import com.musicclubapp.entity.FriendRequest;
import com.musicclubapp.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy znajomosci na prawdziwej bazie - wymaganie nr 14.
 *
 * <p><b>Po co osobny test, skoro jest juz {@code FriendServiceTest}?</b>
 * Bo tamten pracuje na atrapach i obiektach tworzonych przez {@code new} -
 * a najgrozniejszy blad, jaki nas tu spotkal, ujawnia sie WYLACZNIE na
 * prawdziwym Hibernate. Szczegoly przy tescie
 * {@link #friendshipIsSavedBothWaysEvenThroughProxy()}.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Znajomosci - zapis w bazie")
class FriendshipRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendRequestRepository requestRepository;

    @Autowired
    private EntityManager entityManager;

    private User save(String username) {
        return userRepository.save(new User(username, username + "@example.com", "hash"));
    }

    @Test
    @DisplayName("znajomosc zapisuje sie w OBIE strony, nawet gdy encje sa leniwymi proxy")
    void friendshipIsSavedBothWaysEvenThroughProxy() {
        User ala = save("ala");
        User bob = save("bob");
        FriendRequest invitation = requestRepository.save(new FriendRequest(ala, bob));

        /*
         * clear() wyrzuca encje z pamieci sesji. Dzieki temu ponizsze
         * getSender()/getRecipient() zwroca PROXY, a nie te same obiekty,
         * ktore przed chwila zapisalismy - czyli dokladnie taka sytuacje,
         * jaka wystepuje w prawdziwym dzialaniu aplikacji.
         *
         * TO JEST SEDNO TEGO TESTU. Pierwsza wersja metody addFriend()
         * siegala wprost do pola (inny.friends), a nie przez getter. Na
         * zwyklych obiektach dzialalo to bez zarzutu i testy jednostkowe
         * przechodzily - ale odczyt POLA na proxy trafia do pustego pola
         * samego proxy, wiec druga strona znajomosci znikala. Bez bledu,
         * bez ostrzezenia: po prostu jeden z dwoch wierszy sie nie zapisywal.
         */
        entityManager.flush();
        entityManager.clear();

        FriendRequest zPowrotem = requestRepository.findById(invitation.getId()).orElseThrow();
        zPowrotem.getSender().addFriend(zPowrotem.getRecipient());

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.areFriends("ala", "bob"))
            .as("ala powinna miec boba w znajomych").isTrue();
        assertThat(userRepository.areFriends("bob", "ala"))
            .as("bob powinien miec ale w znajomych - TO wlasnie gubila stara wersja").isTrue();

        assertThat(userRepository.countFriends("ala")).isEqualTo(1);
        assertThat(userRepository.countFriends("bob")).isEqualTo(1);
    }

    @Test
    @DisplayName("usuniecie znajomosci kasuje oba wiersze")
    void removalDeletesBothRows() {
        User ala = save("ala");
        User bob = save("bob");
        ala.addFriend(bob);
        entityManager.flush();
        entityManager.clear();

        User alaZBazy = userRepository.findByUsername("ala").orElseThrow();
        User bobZBazy = userRepository.findByUsername("bob").orElseThrow();
        alaZBazy.removeFriend(bobZBazy);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.areFriends("ala", "bob")).isFalse();
        assertThat(userRepository.areFriends("bob", "ala")).isFalse();
    }

    @Test
    @DisplayName("lista znajomych sortuje sie po liczbie WSPOLNYCH znajomych z ogladajacym")
    void sortingBySharedFriends() {
        /*
         * Uklad testowy - warto go przesledzic, bo "wspolni znajomi" liczy sie
         * inaczej, niz podpowiada intuicja. Liczymy osoby, ktore sa znajomymi
         * KANDYDATA i jednoczesnie OGLADAJACEGO - a nie te, ktore laczy
         * kandydata z wlascicielem listy.
         *
         *   ala   - znajomi: cezary, dawid   (jej liste ogladamy)
         *   ela   - znajomi: bob             (ona oglada)
         *   cezary- znajomi: ala, bob
         *   dawid - znajomi: ala
         *
         * cezary i ela maja wspolnego boba  -> 1
         * dawid  i ela nie maja nikogo      -> 0
         * Wiec cezary musi byc nad dawidem.
         */
        User ala = save("ala");
        User bob = save("bob");
        User cezary = save("cezary");
        User dawid = save("dawid");
        User ela = save("ela");

        ala.addFriend(cezary);
        ala.addFriend(dawid);
        ela.addFriend(bob);
        cezary.addFriend(bob);

        entityManager.flush();
        entityManager.clear();

        Page<FriendRow> page = userRepository.friendsRanked(
            "ala", "ela", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);

        assertThat(page.getContent().get(0).getUsername())
            .as("cezary ma wspolnego znajomego z ogladajaca, wiec idzie na gore")
            .isEqualTo("cezary");
        assertThat(page.getContent().get(0).getSharedFriends()).isEqualTo(1);

        assertThat(page.getContent().get(1).getUsername()).isEqualTo("dawid");
        assertThat(page.getContent().get(1).getSharedFriends()).isZero();
    }

    @Test
    @DisplayName("stronicowanie listy znajomych dziala (wymagania nr 3 i 5)")
    void pagingWorks() {
        User ala = save("ala");
        for (int i = 0; i < 7; i++) {
            ala.addFriend(save("znajomy" + i));
        }
        entityManager.flush();
        entityManager.clear();

        Page<FriendRow> pierwsza = userRepository.friendsRanked(
            "ala", "ala", PageRequest.of(0, 3));

        assertThat(pierwsza.getContent()).hasSize(3);
        assertThat(pierwsza.getTotalElements()).isEqualTo(7);
        assertThat(pierwsza.getTotalPages()).isEqualTo(3);
    }
}
