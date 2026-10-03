package com.musicclubapp.gif;

import com.musicclubapp.error.TooManyRequestsException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Najwyzej {@code max} zdarzen na klucz w oknie {@code windowMs} - w pamieci, bo serwer jest jeden. */
final class SlidingLimiter {

    private final long windowMs;
    private final int max;
    private final Map<Object, Deque<Long>> events = new ConcurrentHashMap<>();

    SlidingLimiter(long windowMs, int max) {
        this.windowMs = windowMs;
        this.max = max;
    }

    /** Zapisuje zdarzenie albo rzuca 429, gdy limit juz wyczerpany. */
    void acquire(Object key, long now) {
        Deque<Long> queue = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() <= now - windowMs) {
                queue.pollFirst();
            }
            if (queue.size() >= max) {
                throw new TooManyRequestsException((queue.peekFirst() + windowMs - now + 999) / 1000);
            }
            queue.addLast(now);
        }
    }

    /** Zapomina klucze, z ktorych od okna nic nie przyszlo - zeby mapa nie rosla bez konca. */
    void forgetOld(long now) {
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                while (!e.getValue().isEmpty() && e.getValue().peekFirst() <= now - windowMs) {
                    e.getValue().pollFirst();
                }
                return e.getValue().isEmpty();
            }
        });
    }
}
