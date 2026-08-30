package com.musicclubapp.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Kto do kogo wlasnie pisze - wylacznie w pamieci. */
@Service
public class TypingRegistry {

    /** Jak dlugo od ostatniego sygnalu uznajemy, ze ktos wciaz pisze. */
    public static final int TTL_SECONDS = 5;

    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    /** Po przekroczeniu tylu wpisow przegladamy mape i wyrzucamy przeterminowane. */
    private static final int CLEANUP_THRESHOLD = 500;

    /** "kto→do kogo" → kiedy ostatnio dal znac, ze pisze. */
    private final Map<String, Instant> typing = new ConcurrentHashMap<>();

    /** Odnotowuje, ze {@code sender} pisze do {@code recipient}. */
    public void startedTyping(Long senderId, Long recipientId) {
        if (typing.size() > CLEANUP_THRESHOLD) {
            removeExpired();
        }
        typing.put(key(senderId, recipientId), Instant.now());
    }

    /** Czy sender pisze wlasnie do recipient. */
    public boolean isTyping(Long senderId, Long recipientId) {
        String key = key(senderId, recipientId);
        Instant since = typing.get(key);

        if (since == null) {
            return false;
        }
        if (since.isBefore(Instant.now().minus(TTL))) {
            typing.remove(key, since);
            return false;
        }
        return true;
    }

    /** Kasuje sygnal - uzywane zaraz po wyslaniu wiadomosci. */
    public void stoppedTyping(Long senderId, Long recipientId) {
        typing.remove(key(senderId, recipientId));
    }

    /** Kierunek ma znaczenie: to, ze ja pisze do Ciebie, nie znaczy, ze Ty piszesz do mnie. */
    private String key(Long senderId, Long recipientId) {
        return senderId + "->" + recipientId;
    }

    private void removeExpired() {
        Instant deadline = Instant.now().minus(TTL);
        typing.values().removeIf(since -> since.isBefore(deadline));
    }
}
