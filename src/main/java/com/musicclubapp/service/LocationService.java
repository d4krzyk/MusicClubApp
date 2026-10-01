package com.musicclubapp.service;

import com.musicclubapp.dto.CityHint;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Miasto z profilu i liczenie odleglosci od niego.
 *
 * <p>Zapisujemy wylacznie miasto - nigdy pozycje z telefonu ani adres. Miasto z listy dostaje
 * wspolrzedne srodka miasta; wpisane przez uzytkownika, a nieznane, zostaje tylko tekstem i dziala
 * jako "to samo miasto".</p>
 */
@Service
public class LocationService {

    /** Skad liczymy: klucz miasta i (gdy znamy) jego wspolrzedne. */
    public record Origin(String cityKey, Double latitude, Double longitude) {

        public boolean located() {
            return latitude != null && longitude != null;
        }
    }

    private final UserRepository users;
    private final CityIndex cities;
    private final UserMapper mapper;

    public LocationService(UserRepository users, CityIndex cities, UserMapper mapper) {
        this.users = users;
        this.cities = cities;
        this.mapper = mapper;
    }

    /** Zapisuje miasto; pusty tekst je usuwa. Odsyla konto, tak jak inne zmiany profilu. */
    @Transactional
    public UserResponse update(String username, String text) {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        String typed = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (typed.isEmpty()) {
            user.setCity(null, null, null, null);
        } else {
            CityIndex.City known = cities.find(typed).orElse(null);
            if (known != null) {
                user.setCity(known.name(), EventImportService.cityKey(known.name()),
                    known.latitude(), known.longitude());
            } else {
                user.setCity(typed, EventImportService.cityKey(typed), null, null);
            }
        }
        return mapper.toResponse(user);
    }

    /** Podpowiedzi do pola "Miasto". */
    @Transactional(readOnly = true)
    public List<CityHint> hints(String text) {
        return cities.search(text, 8).stream().map(c -> new CityHint(c.name())).toList();
    }

    /** Skad ogladajacy liczy odleglosci; {@code null}, gdy nie ustawil miasta. */
    public Origin originOf(User user) {
        if (user == null || user.getCityKey() == null) {
            return null;
        }
        return new Origin(user.getCityKey(), user.getCityLatitude(), user.getCityLongitude());
    }

    /**
     * Odleglosc od {@code origin} do miejsca opisanego wspolrzednymi i/albo kluczem miasta, w km.
     * {@code 0.0} - to samo miasto (nawet gdy sale koncertowa dzieli kilka km od srodka), {@code null} -
     * nie wiadomo (brak miasta u ogladajacego albo miejsca spoza listy bez wspolrzednych).
     */
    public Double distanceKm(Origin origin, Double latitude, Double longitude, String cityKey) {
        if (origin == null) {
            return null;
        }
        if (cityKey != null && cityKey.equals(origin.cityKey())) {
            return 0.0;
        }
        if (!origin.located()) {
            return null;
        }
        Double lat = latitude;
        Double lon = longitude;
        if (lat == null || lon == null) {
            CityIndex.City city = cities.byKey(cityKey).orElse(null);
            if (city == null) {
                return null;
            }
            lat = city.latitude();
            lon = city.longitude();
        }
        return CityIndex.distanceKm(origin.latitude(), origin.longitude(), lat, lon);
    }

    /** Odleglosc do miasta podanego tekstem (np. miasto klanu); do "tego samego" wystarcza klucz. */
    public Double distanceToCity(Origin origin, String cityText) {
        String key = EventImportService.cityKey(cityText);
        return key == null ? null : distanceKm(origin, null, null, key);
    }

    /** Zaokraglone do pelnych km - tyle pokazujemy; {@code null} zostaje {@code null}. */
    public static Integer rounded(Double km) {
        return km == null ? null : (int) Math.round(km);
    }
}
