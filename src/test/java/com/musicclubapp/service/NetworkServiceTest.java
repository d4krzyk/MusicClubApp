package com.musicclubapp.service;

import com.musicclubapp.entity.AccountIp;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.AccountIpRepository;
import com.musicclubapp.repository.BlockedIpRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adresy sieciowe: odczyt, blokada i wykrywanie kont z tego samego adresu.
 *
 * <p><b>Najwazniejszy jest tu {@link #spoofedHeaderDoesNotWin()}.</b> Sposob
 * odczytu adresu zza posrednika to miejsce, w ktorym latwo napisac kod
 * dzialajacy w kazdym normalnym uzyciu i bezuzyteczny dokladnie wtedy, gdy
 * ktos probuje go obejsc - a taka blokada nie chroni przed niczym.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Adresy sieciowe i blokady")
class NetworkServiceTest {

    @Autowired private NetworkService network;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountIpRepository accountIpRepository;
    @Autowired private BlockedIpRepository blockedIpRepository;
    @Autowired private EntityManager entityManager;

    private User ala;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        entityManager.flush();
    }

    /**
     * Wlasna instancja serwisu z ustawieniem "za posrednikiem" albo bez.
     *
     * <p>Sam odczyt adresu z zapytania <b>nie dotyka bazy</b>, wiec do tych
     * kilku sprawdzen nie potrzeba ani repozytoriow, ani calego kontekstu.
     * Tworzymy instancje wprost, zamiast przestawiac ustawienie profilu
     * testowego - inaczej kazdy z dwoch wariantow wymagalby osobnej klasy
     * testowej i osobnego uruchomienia calego Springa.</p>
     */
    private NetworkService withProxy(boolean behindProxy) {
        return new NetworkService(accountIpRepository, blockedIpRepository, behindProxy);
    }

    private MockHttpServletRequest requestFrom(String remoteAddr, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        return request;
    }

    /* ------------------------------------------------------------------ */
    /*  Odczyt adresu                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("bez posrednika liczy sie adres rozmowcy, a naglowek jest ignorowany")
    void withoutProxyHeaderIsIgnored() {
        /*
         * Przy backendzie wystawionym wprost (tryb deweloperski) naglowek
         * X-Forwarded-For pisze KLIENT i nikt go nie weryfikuje. Ufanie mu
         * oznaczaloby, ze kazdy podaje dowolny adres, jaki chce.
         */
        MockHttpServletRequest request = requestFrom("10.0.0.5", "1.2.3.4");

        assertThat(withProxy(false).clientIp(request)).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("za posrednikiem liczy sie naglowek, bo adres rozmowcy to sam nginx")
    void behindProxyHeaderWins() {
        /*
         * W ukladzie z docker-compose caly ruch idzie przez nginx frontendu.
         * Bez naglowka KAZDY uzytkownik mialby ten sam adres - adres
         * kontenera nginxa - i cala funkcja bylaby bezuzyteczna.
         */
        MockHttpServletRequest request = requestFrom("172.18.0.4", "203.0.113.7");

        assertThat(withProxy(true).clientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("PODROBIONY naglowek nie wygrywa - bierzemy OSTATNI wpis, nie pierwszy")
    void spoofedHeaderDoesNotWin() {
        /*
         * Sedno calej funkcji. Nasz nginx uzywa $proxy_add_x_forwarded_for,
         * ktore DOKLEJA adres rozmowcy na koniec tego, co przyszlo. Jesli wiec
         * ktos wysle zapytanie z wlasnorecznie napisanym naglowkiem
         * "X-Forwarded-For: 1.2.3.4", do aplikacji dotrze:
         *
         *     1.2.3.4, 203.0.113.7
         *      ^wymyslone   ^prawdziwe, dokleil je nasz nginx
         *
         * Wiekszosc tutoriali kaze brac PIERWSZY wpis - czyli dokladnie ten,
         * ktory napisal atakujacy. Blokade adresu obchodziloby sie wtedy
         * jednym dodatkowym naglowkiem.
         */
        MockHttpServletRequest request = requestFrom("172.18.0.4", "1.2.3.4, 203.0.113.7");

        assertThat(withProxy(true).clientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("pusty naglowek nie przeslania adresu rozmowcy")
    void emptyHeaderFallsBack() {
        assertThat(withProxy(true).clientIp(requestFrom("172.18.0.4", null)))
            .isEqualTo("172.18.0.4");
        assertThat(withProxy(true).clientIp(requestFrom("172.18.0.4", "   ")))
            .isEqualTo("172.18.0.4");
    }

    @Test
    @DisplayName("brak zapytania nie wywraca odczytu")
    void noRequestIsHandled() {
        // Zdarza sie w zadaniach uruchamianych poza zapytaniem HTTP
        assertThat(withProxy(true).clientIp(null)).isEqualTo("unknown");
    }

    /* ------------------------------------------------------------------ */
    /*  Blokada                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("zablokowany adres jest odrzucany")
    void blockedAddressIsRefused() {
        network.block("admin", "10.0.0.1", "198.51.100.9", "Wracal po banie");

        assertThatThrownBy(() -> network.requireNotBlocked("198.51.100.9"))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("zablokowany");

        // Inne adresy dzialaja normalnie
        network.requireNotBlocked("198.51.100.10");
    }

    @Test
    @DisplayName("administrator NIE MOZE zablokowac adresu, z ktorego wlasnie korzysta")
    void adminCannotLockHimselfOut() {
        /*
         * To nie jest ostroznosc teoretyczna. Przy testowaniu na jednym
         * komputerze albo w sieci firmowej administrator siedzi za tym samym
         * adresem co osoba, ktora blokuje - i jednym klknieciem odcialby sobie
         * mozliwosc ponownego zalogowania. Odzyskanie dostepu wymagaloby
         * wtedy recznej zmiany w bazie.
         */
        assertThatThrownBy(() -> network.block("admin", "203.0.113.7", "203.0.113.7", "pomylka"))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(blockedIpRepository.existsByAddress("203.0.113.7")).isFalse();
    }

    @Test
    @DisplayName("powtorne zablokowanie tego samego adresu nie tworzy drugiego wpisu")
    void blockingTwiceIsHarmless() {
        network.block("admin", "10.0.0.1", "198.51.100.9", "pierwszy powod");
        network.block("admin", "10.0.0.1", "198.51.100.9", "drugi powod");

        assertThat(blockedIpRepository.findAll())
            .filteredOn(entry -> entry.getAddress().equals("198.51.100.9"))
            .hasSize(1)
            // Zostaje pierwszy powod - to on opisuje, dlaczego blokada powstala
            .allMatch(entry -> entry.getReason().equals("pierwszy powod"));
    }

    /* ------------------------------------------------------------------ */
    /*  Historia logowan i multikonta                                      */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("pierwsze logowanie zapisuje adres, kolejne tylko zwieksza licznik")
    void repeatedLoginBumpsTheCounter() {
        /*
         * Jeden wiersz na pare (konto, adres), a nie na kazde logowanie -
         * inaczej tabela roslaby w nieskonczonosc, a odpowiedz na jedyne
         * pytanie, ktore nas interesuje, bylaby taka sama.
         */
        network.recordLogin(ala, "203.0.113.7");
        network.recordLogin(ala, "203.0.113.7");
        network.recordLogin(ala, "203.0.113.7");
        entityManager.flush();

        List<AccountIp> addresses = network.addressesOf(ala.getId());

        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).getLoginCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("konto z tego samego adresu wychodzi jako poszlaka multikonta")
    void findsAccountsSharingAnAddress() {
        User drugie = userRepository.save(new User("ala2", "ala2@example.com", "hash"));
        User obcy = userRepository.save(new User("obcy", "obcy@example.com", "hash"));
        entityManager.flush();

        network.recordLogin(ala, "203.0.113.7");
        network.recordLogin(drugie, "203.0.113.7");
        network.recordLogin(obcy, "198.51.100.1");
        entityManager.flush();

        assertThat(network.relatedAccounts(ala.getId()))
            .extracting(entry -> entry.getUser().getUsername())
            // Ani samego siebie, ani osoby z innego adresu
            .containsExactly("ala2");
    }

    @Test
    @DisplayName("konto bez wspolnych adresow nie ma powiazan")
    void noSharedAddressMeansNoRelations() {
        network.recordLogin(ala, "203.0.113.7");
        entityManager.flush();

        assertThat(network.relatedAccounts(ala.getId())).isEmpty();
    }

    @Test
    @DisplayName("skasowanie konta zabiera jego historie adresow")
    void forgettingUserRemovesHistory() {
        network.recordLogin(ala, "203.0.113.7");
        entityManager.flush();

        network.deleteAllOf(ala.getId());
        entityManager.flush();

        assertThat(accountIpRepository.findByUserIdOrderByLastSeenAtDesc(ala.getId())).isEmpty();
    }
}
