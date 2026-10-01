package com.musicclubapp.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Miasta Polski ze wspolrzednymi - po to, zeby wiedziec, jak daleko jest "Poznan" od "Gniezna" i
 * od koncertu w "Warsaw".
 *
 * <p>Lista siedzi w pliku {@code geo/miasta.csv} i laduje sie raz, przy starcie. Miasto
 * rozpoznajemy po tym samym kluczu, ktorego uzywa import wydarzen ({@link EventImportService#cityKey}):
 * male litery, bez polskich znakow. Dzieki temu "Poznań", "poznan " i "Poznan" z Ticketmastera to
 * jedno miasto, a "Warsaw" (tak pisze je Ticketmaster) trafia do Warszawy.</p>
 *
 * <p>To nie jest geokoder: miasta spoza listy nie ma. Tekst, ktorego nie znamy, dziala jako "to samo
 * miasto" (po kluczu), ale bez odleglosci.</p>
 */
@Component
public class CityIndex {

    /** Promien Ziemi w kilometrach. */
    private static final double R_KM = 6371.0;

    /** Jedno miasto z listy; {@code key} to klucz z {@link EventImportService#cityKey}. */
    public record City(String name, String key, double latitude, double longitude) {
    }

    /** Miasto razem ze wszystkimi kluczami, pod ktorymi je znamy (nazwa i jej odmiany). */
    private record Entry(City city, List<String> keys) {
    }

    /** Miasta w kolejnosci z pliku (mniej wiecej od najwiekszego) - tak podpowiada pole "Miasto". */
    private final List<Entry> cities = new ArrayList<>();

    /** Klucz nazwy albo jej odmiany -> miasto. */
    private final Map<String, City> byKey = new HashMap<>();

    @PostConstruct
    void load() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
            new ClassPathResource("geo/miasta.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(";", -1);
                City city = new City(parts[0].strip(), EventImportService.cityKey(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                List<String> keys = new ArrayList<>(List.of(city.key()));
                byKey.putIfAbsent(city.key(), city);
                if (parts.length > 3 && !parts[3].isBlank()) {
                    for (String alias : parts[3].split(",")) {
                        String key = EventImportService.cityKey(alias);
                        if (key != null) {
                            keys.add(key);
                            byKey.putIfAbsent(key, city);
                        }
                    }
                }
                cities.add(new Entry(city, keys));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Nie da sie wczytac listy miast (geo/miasta.csv)", e);
        }
    }

    /** Miasto po kluczu (jak w {@code music_events.city_key}) albo po kluczu jego innej nazwy. */
    public Optional<City> byKey(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(byKey.get(key));
    }

    /** Miasto z wpisanego tekstu: "Poznań", "poznan", "Warsaw" itd. */
    public Optional<City> find(String text) {
        return byKey(EventImportService.cityKey(text));
    }

    /**
     * Podpowiedzi do pola "Miasto": najpierw nazwy zaczynajace sie od wpisanego tekstu, potem takie,
     * ktore go zawieraja. Kolejnosc pliku (od wiekszych miast) rozstrzyga remisy.
     */
    public List<City> search(String text, int limit) {
        String key = EventImportService.cityKey(text);
        if (key == null || limit <= 0) {
            return List.of();
        }
        List<City> prefix = new ArrayList<>();
        List<City> inside = new ArrayList<>();
        for (Entry entry : cities) {
            if (entry.keys().stream().anyMatch(k -> k.startsWith(key))) {
                prefix.add(entry.city());
            } else if (entry.keys().stream().anyMatch(k -> k.contains(key))) {
                inside.add(entry.city());
            }
        }
        prefix.addAll(inside);
        return prefix.size() <= limit ? prefix : prefix.subList(0, limit);
    }

    /** Odleglosc w linii prostej w kilometrach (wzor haversine). */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
