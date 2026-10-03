package com.musicclubapp.gif;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Maly pamiec podreczna z czasem waznosci i limitem wpisow (najdawniej uzywane odpadaja). Bezpieczna dla watkow. */
final class TtlCache<V> {

    private record Slot<V>(V value, long expires) { }

    private final long ttlMs;
    private final Map<String, Slot<V>> map;

    TtlCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Slot<V>> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Wartosc z pamieci, a gdy jej nie ma albo wygasla - wylicza ja {@code loader} (poza blokada). */
    V get(String key, long now, Supplier<V> loader) {
        synchronized (map) {
            Slot<V> found = map.get(key);
            if (found != null && found.expires() > now) {
                return found.value();
            }
        }
        V loaded = loader.get();
        synchronized (map) {
            map.put(key, new Slot<>(loaded, now + ttlMs));
        }
        return loaded;
    }

    int size() {
        synchronized (map) {
            return map.size();
        }
    }
}
