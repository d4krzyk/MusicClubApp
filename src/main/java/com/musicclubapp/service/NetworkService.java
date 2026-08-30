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

/** Adresy sieciowe: skad kto sie loguje i ktore adresy sa zablokowane. */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    private final AccountIpRepository accountIpRepository;
    private final BlockedIpRepository blockedIpRepository;

    /**
     * Czy przed aplikacja stoi wlasny posrednik (nginx), ktory dokleja naglowek X-Forwarded-For.
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

    /** Adres, z ktorego naprawde przyszlo zapytanie. */
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

    /** Przerywa, gdy adres jest zablokowany. */
    @Transactional(readOnly = true)
    public void requireNotBlocked(String address) {
        if (blockedIpRepository.existsByAddress(address)) {
            log.info("Odrzucono probe z zablokowanego adresu {}", address);
            throw OperationNotAllowedException.blockedAddress();
        }
    }

    /** Blokuje adres. */
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

    /** Odnotowuje udane logowanie z danego adresu. */
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

    /** Konta logujace sie z tych samych adresow co wskazane. */
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
