package com.musicclubapp.service;

import com.musicclubapp.entity.AccountIp;
import com.musicclubapp.entity.BlockedIp;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.AccountIpRepository;
import com.musicclubapp.repository.BlockedIpRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adresy sieciowe: skad kto sie loguje i ktore adresy sa zablokowane.
 *
 * <p>Dwie rzeczy, ktore robi ta klasa, sa ze soba scisle zwiazane: zeby
 * zablokowac adres, trzeba najpierw wiedziec, ktory to - a zeby to wiedziec,
 * trzeba go poprawnie odczytac z zapytania. Ten drugi krok jest znacznie
 * mniej oczywisty, niz wyglada.</p>
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    private final AccountIpRepository accountIpRepository;
    private final BlockedIpRepository blockedIpRepository;

    /**
     * Czy przed aplikacja stoi wlasny posrednik (nginx), ktory dokleja
     * naglowek {@code X-Forwarded-For}.
     *
     * <p>W ukladzie z {@code docker-compose.yml} stoi - caly ruch idzie przez
     * nginx frontendu, wiec bez tego naglowka kazdy uzytkownik mialby ten sam
     * adres: adres kontenera nginxa. Przy uruchomieniu backendu wprost
     * (tryb deweloperski) posrednika nie ma i naglowka nie wolno ufac.</p>
     */
    private final boolean behindProxy;

    public NetworkService(AccountIpRepository accountIpRepository,
                          BlockedIpRepository blockedIpRepository,
                          @Value("${app.security.behind-proxy:true}") boolean behindProxy) {
        this.accountIpRepository = accountIpRepository;
        this.blockedIpRepository = blockedIpRepository;
        this.behindProxy = behindProxy;
    }

    /* ------------------------------------------------------------------ */
    /*  Odczyt adresu                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Adres, z ktorego naprawde przyszlo zapytanie.
     *
     * <p><b>Bierzemy OSTATNI wpis z {@code X-Forwarded-For}, nie pierwszy.</b>
     * To jest cala sztuczka i warto ja rozumiec, bo intuicja podpowiada
     * odwrotnie.</p>
     *
     * <p>Naglowek jest lista: {@code klient, posrednik1, posrednik2}. Nasz
     * nginx uzywa {@code $proxy_add_x_forwarded_for}, ktore <b>dokleja</b>
     * adres rozmowcy na KONIEC tego, co przyszlo. Jesli wiec ktos wysle
     * zapytanie z podrobionym naglowkiem {@code X-Forwarded-For: 1.2.3.4},
     * do aplikacji dotrze {@code 1.2.3.4, <jego prawdziwy adres>}.</p>
     *
     * <p>Pierwszy wpis jest zatem tym, ktory <b>napisal atakujacy</b>, a ostatni
     * tym, ktory <b>dokleil nasz wlasny nginx</b> - i tylko on jest wiarygodny.
     * Branie pierwszego wpisu (co robi wiekszosc tutoriali) sprawiloby, ze
     * blokade adresu obchodzi sie jednym dodatkowym naglowkiem.</p>
     *
     * <p>Przy jednym posredniku ta regula jest scisla. Przy lancuchu kilku
     * posrednikow trzeba by liczyc od konca tyle wpisow, ile ich jest -
     * ale tego ukladu tutaj nie ma i dopisywanie go "na wszelki wypadek"
     * dodaloby ustawienie, ktorego nikt nie umialby poprawnie ustawic.</p>
     */
    public String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        if (behindProxy) {
            String forwarded = request.getHeader(FORWARDED_HEADER);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) {
                    return shorten(last);
                }
            }
        }

        String direct = request.getRemoteAddr();
        return direct == null ? "unknown" : shorten(direct);
    }

    /** Kolumna ma 45 znakow - dluzsza wartosc to i tak nie jest adres. */
    private String shorten(String address) {
        return address.length() > AccountIp.MAX_ADDRESS_LENGTH
            ? address.substring(0, AccountIp.MAX_ADDRESS_LENGTH)
            : address;
    }

    /* ------------------------------------------------------------------ */
    /*  Blokada                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Przerywa, gdy adres jest zablokowany.
     *
     * <p>Wolane przy rejestracji i logowaniu - i tylko tam. Dlaczego akurat
     * tam, opisuje {@link BlockedIp}.</p>
     */
    @Transactional(readOnly = true)
    public void requireNotBlocked(String address) {
        if (blockedIpRepository.existsByAddress(address)) {
            log.info("Odrzucono probe z zablokowanego adresu {}", address);
            throw OperationNotAllowedException.blockedAddress();
        }
    }

    /**
     * Blokuje adres.
     *
     * <p><b>Administrator nie moze zablokowac adresu, z ktorego sam wlasnie
     * jest.</b> To nie jest teoretyczna ostroznosc: przy testowaniu na jednym
     * komputerze albo w sieci firmowej administrator siedzi za tym samym
     * adresem co osoba, ktora blokuje - i jednym klknieciem odcialby sobie
     * mozliwosc ponownego zalogowania. Odzyskanie dostepu wymagaloby wtedy
     * recznej zmiany w bazie.</p>
     */
    @Transactional
    public BlockedIp block(String adminUsername, String adminAddress,
                           String address, String reason) {

        if (address.equals(adminAddress)) {
            throw OperationNotAllowedException.ownAddress();
        }

        return blockedIpRepository.findByAddress(address)
            // Ten adres juz jest zablokowany - powtorne blokowanie niczego
            // nie zmienia i nie jest bledem
            .orElseGet(() -> blockedIpRepository.save(
                new BlockedIp(address, reason, adminUsername)));
    }

    @Transactional
    public void unblock(Long id) {
        blockedIpRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<BlockedIp> blockedAddresses() {
        return blockedIpRepository.findAll();
    }

    /* ------------------------------------------------------------------ */
    /*  Historia logowan                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Odnotowuje udane logowanie z danego adresu.
     *
     * <p><b>Blad tutaj nie moze przerwac logowania.</b> To jest notatka
     * pomocnicza dla administratora, a nie warunek wejscia - odmowa
     * zalogowania z powodu problemu przy zapisie historii bylaby awaria
     * wywolana funkcja pomocnicza.</p>
     */
    @Transactional
    public void recordLogin(User user, String address) {
        try {
            accountIpRepository.findByUserIdAndAddress(user.getId(), address)
                .ifPresentOrElse(
                    AccountIp::seenAgain,
                    () -> accountIpRepository.save(new AccountIp(user, address)));
        } catch (Exception e) {
            log.warn("Nie udalo sie zapisac adresu logowania dla {}: {}",
                user.getUsername(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AccountIp> addressesOf(Long userId) {
        return accountIpRepository.findByUserIdOrderByLastSeenAtDesc(userId);
    }

    /**
     * Konta logujace sie z tych samych adresow co wskazane.
     *
     * <p><b>To jest poszlaka, a nie dowod</b> - patrz komentarz przy
     * {@link AccountIp}. Aplikacja nigdy nie blokuje nikogo automatycznie
     * na tej podstawie; decyzje podejmuje czlowiek, ktory widzi tez, ile
     * razy i kiedy z tego adresu korzystano.</p>
     */
    @Transactional(readOnly = true)
    public List<AccountIp> relatedAccounts(Long userId) {
        return accountIpRepository.sharingAddressWith(userId);
    }

    /** Kasuje historie adresow konta - przy usuwaniu uzytkownika. */
    @Transactional
    public void deleteAllOf(Long userId) {
        accountIpRepository.deleteByUserId(userId);
    }
}
