package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Odczyt historii sluchania z <b>Last.fm</b>.
 *
 * <p><b>Dlaczego Last.fm, a nie Spotify.</b> Pelne API Spotify wymaga, zeby
 * kazdy uzytkownik byl RECZNIE wpisany na liste w panelu dewelopera - a lista
 * ma limit pieciu osob. Zdjecie tego limitu wymaga zarejestrowanej firmy.
 * W projekcie zaliczeniowym to sciana nie do przejscia. Last.fm potrzebuje
 * tylko <b>klucza aplikacji i nazwy uzytkownika</b>: bez logowania, bez OAuth
 * i bez limitu kont. Do tego zbiera historie z wielu zrodel naraz - takze ze
 * Spotify, jesli ktos ma tam wlaczony scrobbling.</p>
 *
 * <p><b>Ten serwis nie zwraca zdjec i to nie jest przeoczenie.</b> Last.fm
 * od 2019 roku oddaje w miejscu zdjecia artysty stala szara ikonke - te sama
 * dla wszystkich. Dlatego z Last.fm bierzemy WYLACZNIE nazwy, a kto to
 * naprawde jest (identyfikator, zdjecie) ustala potem {@link
 * DeezerCatalogService}. Podzial jest wiec taki:
 * <b>Last.fm mowi CZEGO sluchasz, Deezer mowi KTO to jest.</b></p>
 *
 * <p><b>Klucz jest opcjonalny.</b> Bez niego metody zwracaja puste listy,
 * a {@link #dostepne()} - {@code false}. Frontend chowa wtedy przycisk
 * importu, a reszta profilu (reczne dodawanie z Deezera) dziala normalnie.
 * Zaden uzytkownik nie zobaczy bledu tylko dlatego, ze ktos nie ustawil
 * zmiennej srodowiskowej.</p>
 */
@Service
public class LastFmService {

    private static final Logger log = LoggerFactory.getLogger(LastFmService.class);


    /**
     * Etykiety, ktore w Last.fm sa najpopularniejsze, ale gatunkiem NIE sa.
     *
     * <p>Tagi na Last.fm wpisuja uzytkownicy, wiec obok "shoegaze" i "trip hop"
     * trafiaja sie "seen live", "favorites" i "albums i own". Bez odsiania
     * takich etykiet dopasowanie po gatunkach laczyloby ludzi na zasadzie
     * "oboje byli na jakims koncercie".</p>
     */
    private static final Set<String> NOT_GENRES = Set.of(
        "seen live", "favorites", "favourites", "albums i own", "my music",
        "awesome", "beautiful", "love", "favorite songs", "spotify", "under 2000 listeners");

    /**
     * Prog popularnosci tagu (Last.fm podaje 0-100).
     *
     * <p>Ponizej tej wartosci to zwykle etykiety wpisane przez jedna osobe.
     * Wpuszczenie ich do bazy zamienialoby gatunki w losowy szum.</p>
     */
    private static final int TAG_THRESHOLD = 30;

    /** Ile gatunkow zapisujemy przy jednym artyscie. */
    private static final int MAX_GENRES = 5;

    private final RestClient restClient;
    private final String key;

    /** Adres API - wyciagniety do ustawien, zeby dalo sie go podstawic w tescie. */
    private final String api;

    public LastFmService(
            @Value("${app.lastfm.api-key:}") String key,
            @Value("${app.lastfm.base-url:https://ws.audioscrobbler.com/2.0/}") String api,
            @Value("${app.music.catalog.timeout-ms:4000}") int timeoutMs) {

        this.key = key == null ? "" : key.trim();
        this.api = api;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        if (this.key.isEmpty()) {
            log.info("Klucz Last.fm nie jest ustawiony (app.lastfm.api-key) - "
                + "import historii sluchania bedzie wylaczony. "
                + "Reczne dodawanie ulubionych z katalogu Deezera dziala normalnie.");
        }
    }

    /** Czy import z Last.fm jest w ogole mozliwy (czy jest klucz). */
    public boolean available() {
        return !key.isEmpty();
    }

    /** Nazwa utworu wraz z wykonawca - tyle, ile Last.fm o nim mowi. */
    public record ListenedTrack(String artistName, String title) { }

    /**
     * Najczesciej sluchani artysci danego uzytkownika Last.fm.
     *
     * @return same nazwy, w kolejnosci od najczesciej sluchanego
     * @throws IllegalArgumentException gdy Last.fm nie zna takiego uzytkownika
     */
    public List<String> topArtists(String user, int limit) {
        JsonNode response = ask("user.gettopartists", user, limit);

        List<String> names = new ArrayList<>();
        JsonNode list = enter(response, "topartists", "artist");
        for (JsonNode a : list) {
            String name = text(a, "name");
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    /** Najczesciej sluchane utwory danego uzytkownika Last.fm. */
    public List<ListenedTrack> topTracks(String user, int limit) {
        JsonNode response = ask("user.gettoptracks", user, limit);

        List<ListenedTrack> tracks = new ArrayList<>();
        JsonNode list = enter(response, "toptracks", "track");
        for (JsonNode u : list) {
            JsonNode artistName = u.get("artist");
            String title = text(u, "name");
            if (title != null && artistName != null) {
                tracks.add(new ListenedTrack(text(artistName, "name"), title));
            }
        }
        return tracks;
    }

    /**
     * Gatunki artysty - z tagow Last.fm, odsianych z etykiet niebedacych gatunkiem.
     *
     * <p>Wynik jest <b>przyblizeniem</b>. Tagi wpisuja ludzie, wiec bywaja
     * niescisle albo sporne ("czy to jeszcze indie, czy juz pop"). Do tego,
     * do czego ich uzywamy - zblizenia do siebie osob, ktore nie maja
     * wspolnego ani jednego wykonawcy - to w zupelnosci wystarczy.</p>
     *
     * <p>Blad przy pobieraniu konczy sie pustym zbiorem, a nie wyjatkiem:
     * artysta bez gatunkow jest calkowicie poprawnym stanem.</p>
     */
    public Set<String> artistGenres(String artistName) {
        if (!available() || artistName == null || artistName.isBlank()) {
            return Set.of();
        }

        try {
            String url = UriComponentsBuilder.fromUriString(api)
                .queryParam("method", "artist.gettoptags")
                .queryParam("artist", artistName)
                .queryParam("api_key", key)
                .queryParam("format", "json")
                .queryParam("autocorrect", 1)
                .build()
                .toUriString();

            JsonNode response = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (response == null || response.has("error")) {
                return Set.of();
            }

            Set<String> genres = new LinkedHashSet<>();
            for (JsonNode tag : enter(response, "toptags", "tag")) {
                if (genres.size() >= MAX_GENRES) {
                    break;
                }
                JsonNode count = tag.get("count");
                String name = text(tag, "name");

                if (name == null || count == null || count.asInt() < TAG_THRESHOLD) {
                    continue;
                }
                String normalized = name.toLowerCase(Locale.ROOT).trim();
                if (!NOT_GENRES.contains(normalized) && normalized.length() <= 60) {
                    genres.add(normalized);
                }
            }
            return genres;

        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac gatunkow dla '{}': {}", artistName, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Wspolna czesc obu zapytan o historie sluchania.
     *
     * <p><b>Tu blad JEST rzucany dalej</b> - inaczej niz przy gatunkach.
     * Roznica jest istotna: literowka w nazwie uzytkownika Last.fm to pomylka,
     * o ktorej trzeba powiedziec. Cicho zwrocona pusta lista wygladalaby jak
     * "nic nie sluchasz" i nikt by nie wiedzial, ze wpisal zla nazwe.</p>
     */
    private JsonNode ask(String method, String user, int limit) {
        if (!available()) {
            throw new IllegalStateException("Brak klucza Last.fm");
        }

        String url = UriComponentsBuilder.fromUriString(api)
            .queryParam("method", method)
            .queryParam("user", user)
            .queryParam("api_key", key)
            .queryParam("format", "json")
            .queryParam("period", "overall")
            .queryParam("limit", Math.max(1, Math.min(limit, 50)))
            .build()
            .toUriString();

        JsonNode response;
        try {
            response = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            log.warn("Last.fm nie odpowiedzial ({}): {}", method, e.getMessage());
            throw new IllegalStateException("Last.fm nie odpowiada", e);
        }

        /*
         * Last.fm zwraca bledy POLEM "error" w tresci odpowiedzi. Kod 6
         * oznacza "nie ma takiego uzytkownika" - i to jedyny przypadek,
         * ktory uzytkownik naszej aplikacji moze sam naprawic.
         */
        if (response == null || response.has("error")) {
            log.warn("Last.fm odmowil ({}): {}", method,
                response == null ? "pusta odpowiedz" : response.get("message"));
            throw new IllegalArgumentException("Last.fm nie zna uzytkownika: " + user);
        }
        return response;
    }

    /**
     * Wchodzi w zagniezdzona tablice odpowiedzi.
     *
     * <p>Last.fm pakuje wyniki w dwa poziomy ({@code topartists.artist}),
     * a przy jednym wyniku potrafi oddac obiekt zamiast tablicy. Petla
     * {@code for} po pojedynczym obiekcie przeszlaby po jego POLACH, wiec
     * takie przypadki odsiewamy tutaj, a nie w kazdym miejscu z osobna.</p>
     */
    private JsonNode enter(JsonNode root, String external, String internal) {
        JsonNode level = root.get(external);
        if (level == null) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        JsonNode list = level.get(internal);
        return list == null || !list.isArray()
            ? com.fasterxml.jackson.databind.node.MissingNode.getInstance()
            : list;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
