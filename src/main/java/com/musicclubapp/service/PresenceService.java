package com.musicclubapp.service;

import com.musicclubapp.dto.PresenceResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Kto jest teraz aktywny i kiedy byl ostatnio - jedno miejsce na cala regule. */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** Ile minut od ostatniej aktywnosci uznajemy jeszcze za "online". */
    public static final int ONLINE_MINUTES = 3;

    /** Jak czesto najwyzej zapisujemy date do bazy. */
    public static final int WRITE_EVERY_SECONDS = 45;

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(ONLINE_MINUTES);
    private static final Duration WRITE_EVERY = Duration.ofSeconds(WRITE_EVERY_SECONDS);

    /** Zabezpieczenie przed rozrostem mapy. */
    private static final int MAX_TRACKED = 10_000;

    /** Login → kiedy ostatnio zapisalismy jego date do bazy. */
    private final Map<String, LocalDateTime> lastWrite = new ConcurrentHashMap<>();

    private final UserRepository userRepository;

    public PresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Odnotowuje, ze ta osoba wlasnie cos zrobila. */
    @Transactional
    public void touch(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime written = lastWrite.get(username);

        if (written != null && written.isAfter(now.minus(WRITE_EVERY))) {
            return;   // zapisane niedawno - nie ma po co wracac do bazy
        }

        try {
            if (lastWrite.size() > MAX_TRACKED) {
                lastWrite.clear();
            }
            /* Zapis jednym UPDATE, bez wczytywania encji. */
            userRepository.touchLastSeen(username, now);
            lastWrite.put(username, now);
        } catch (Exception e) {
            log.debug("Nie udalo sie zapisac aktywnosci uzytkownika {}: {}",
                username, e.getMessage());
        }
    }

    /** Obecnosc danej osoby - do wstawienia w DTO profilu, kafelka czy rozmowy. */
    public PresenceResponse of(User user) {
        return of(user == null ? null : user.getLastSeenAt());
    }

    /** Obecnosc wyliczona z samej daty. */
    public PresenceResponse of(LocalDateTime lastSeenAt) {
        return new PresenceResponse(isOnline(lastSeenAt), lastSeenAt);
    }

    /** Czy data ostatniej aktywnosci miesci sie jeszcze w oknie "online". */
    public boolean isOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null
            && lastSeenAt.isAfter(LocalDateTime.now().minus(ONLINE_WINDOW));
    }

    /** Czysci licznik zapisow - wylacznie na potrzeby testow. */
    public void forgetWriteThrottle() {
        lastWrite.clear();
    }
}
