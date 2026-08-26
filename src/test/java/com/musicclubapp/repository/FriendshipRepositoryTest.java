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
 * {@link #znajomoscZapisujeSieWObieStronyNawetPrzezProxy()}.</p>
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

    private User zapisz(String login) {
        return userRepository.save(new User(login, login + "@example.com", "hash"));
    }

    @Test
    @DisplayName("znajomosc zapisuje sie w OBIE strony, nawet gdy encje sa leniwymi proxy")
    void znajomoscZapisujeSieWObieStronyNawetPrzezProxy() {
        User ala = zapisz("ala");
        User bob = zapisz("bob");
        FriendRequest zaproszenie = requestRepository.save(new FriendRequest(ala, bob));

        /*
         * clear() wyrzuca encje z pamieci sesji. Dzieki temu ponizsze
         * getSender()/getRecipient() zwroca PROXY, a nie te same obiekty,
         * ktore przed chwila zapisalismy - czyli dokladnie taka sytuacje,
         * jaka wystepuje w prawdziwym dzialaniu aplikacji.
         *
         * TO JEST SEDNO TEGO TESTU. Pierwsza wersja metody dodajZnajomego()
         * siegala wprost do pola (inny.friends), a nie przez getter. Na
         * zwyklych obiektach dzialalo to bez zarzutu i testy jednostkowe
         * przechodzily - ale odczyt POLA na proxy trafia do pustego pola
         * samego proxy, wiec druga strona znajomosci znikala. Bez bledu,
         * bez ostrzezenia: po prostu jeden z dwoch wierszy sie nie zapisywal.
         */
        entityManager.flush();
        entityManager.clear();

        FriendRequest zPowrotem = requestRepository.findById(zaproszenie.getId()).orElseThrow();
        zPowrotem.getSender().dodajZnajomego(zPowrotem.getRecipient());

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.czySaZnajomymi("ala", "bob"))
            .as("ala powinna miec boba w znajomych").isTrue();
        assertThat(userRepository.czySaZnajomymi("bob", "ala"))
            .as("bob powinien miec ale w znajomych - TO wlasnie gubila stara wersja").isTrue();

        assertThat(userRepository.policzZnajomych("ala")).isEqualTo(1);
        assertThat(userRepository.policzZnajomych("bob")).isEqualTo(1);
    }

    @Test
    @DisplayName("usuniecie znajomosci kasuje oba wiersze")
    void usuniecieKasujeObaWiersze() {
        User ala = zapisz("ala");
        User bob = zapisz("bob");
        ala.dodajZnajomego(bob);
        entityManager.flush();
        entityManager.clear();

        User alaZBazy = userRepository.findByUsername("ala").orElseThrow();
        User bobZBazy = userRepository.findByUsername("bob").orElseThrow();
        alaZBazy.usunZnajomego(bobZBazy);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.czySaZnajomymi("ala", "bob")).isFalse();
        assertThat(userRepository.czySaZnajomymi("bob", "ala")).isFalse();
    }

    @Test
    @DisplayName("lista znajomych sortuje sie po liczbie WSPOLNYCH znajomych z ogladajacym")
    void sortowaniePoWspolnychZnajomych() {
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
        User ala = zapisz("ala");
        User bob = zapisz("bob");
        User cezary = zapisz("cezary");
        User dawid = zapisz("dawid");
        User ela = zapisz("ela");

        ala.dodajZnajomego(cezary);
        ala.dodajZnajomego(dawid);
        ela.dodajZnajomego(bob);
        cezary.dodajZnajomego(bob);

        entityManager.flush();
        entityManager.clear();

        Page<FriendRow> strona = userRepository.znajomiPosortowani(
            "ala", "ela", PageRequest.of(0, 10));

        assertThat(strona.getTotalElements()).isEqualTo(2);

        assertThat(strona.getContent().get(0).getUsername())
            .as("cezary ma wspolnego znajomego z ogladajaca, wiec idzie na gore")
            .isEqualTo("cezary");
        assertThat(strona.getContent().get(0).getWspolniZnajomi()).isEqualTo(1);

        assertThat(strona.getContent().get(1).getUsername()).isEqualTo("dawid");
        assertThat(strona.getContent().get(1).getWspolniZnajomi()).isZero();
    }

    @Test
    @DisplayName("stronicowanie listy znajomych dziala (wymagania nr 3 i 5)")
    void stronicowanieDziala() {
        User ala = zapisz("ala");
        for (int i = 0; i < 7; i++) {
            ala.dodajZnajomego(zapisz("znajomy" + i));
        }
        entityManager.flush();
        entityManager.clear();

        Page<FriendRow> pierwsza = userRepository.znajomiPosortowani(
            "ala", "ala", PageRequest.of(0, 3));

        assertThat(pierwsza.getContent()).hasSize(3);
        assertThat(pierwsza.getTotalElements()).isEqualTo(7);
        assertThat(pierwsza.getTotalPages()).isEqualTo(3);
    }
}
