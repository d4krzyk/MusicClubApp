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
import java.util.Optional;
import java.util.Set;

/** Odczyt historii sluchania z Last.fm. */
@Service
public class LastFmService {

    private static final Logger log = LoggerFactory.getLogger(LastFmService.class);


    /** Etykiety, ktore w Last.fm sa najpopularniejsze, ale gatunkiem NIE sa. */
    private static final Set<String> NOT_GENRES = Set.of(
        "seen live", "favorites", "favourites", "albums i own", "my music",
        "awesome", "beautiful", "love", "favorite songs", "spotify", "under 2000 listeners");

    /** Prog popularnosci tagu (Last.fm podaje 0-100). */
    private static final int TAG_THRESHOLD = 30;

    /** Ile gatunkow zapisujemy przy jednym artyscie. */
    private static final int MAX_GENRES = 5;

    /** Kod bledu Last.fm "nie ma takiego artysty". */
    private static final int LASTFM_BRAK_ARTYSTY = 6;

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

    /** Najczesciej sluchani artysci danego uzytkownika Last.fm. */
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

    /** Gatunki artysty - z tagow Last.fm, odsianych z etykiet niebedacych gatunkiem. */
    public Set<String> artistGenres(String artistName) {
        return lookupArtistGenres(artistName).orElse(Set.of());
    }

    /**
     * To samo, ale z rozroznieniem "artysta nie ma tagow" (pusty zbior) od
     * "Last.fm nie odpowiedzial" (pusty Optional).
     *
     * Pamiec podreczna gatunkow wykonawcow zapisuje wynik na dlugo. Gdyby
     * awaria Last.fm wygladala tak samo jak brak tagow, jedna przerwa w jego
     * dzialaniu zostawilaby setki wykonawcow bez gatunkow na dwa miesiace.
     */
    public Optional<Set<String>> lookupArtistGenres(String artistName) {
        if (!available()) {
            return Optional.empty();
        }
        if (artistName == null || artistName.isBlank()) {
            return Optional.of(Set.of());
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
            if (response == null) {
                return Optional.empty();
            }
            if (response.has("error")) {
                /* 6 = "nie ma takiego artysty" - to odpowiedz, a nie awaria. */
                return response.path("error").asInt() == LASTFM_BRAK_ARTYSTY
                    ? Optional.of(Set.of())
                    : Optional.empty();
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
            return Optional.of(genres);

        } catch (Exception e) {
            /*
             * Tresc wyjatku Springa zawiera caly adres zapytania, a w nim
             * api_key - wiec klucz podmieniamy, zanim cokolwiek trafi do logu.
             */
            log.warn("Nie udalo sie pobrac gatunkow dla '{}': {}", artistName, bezKlucza(e.getMessage()));
            return Optional.empty();
        }
    }

    /** Opis wykonawcy z Last.fm: nazwa po poprawce, krotkie bio (zwykly tekst), adres strony, sluchacze, podobni. */
    public record ArtistInfo(String name, String bio, String url, Long listeners, List<String> similar) { }

    /** Ile znakow opisu zostawiamy - to podglad, pelny opis jest pod linkiem. */
    static final int MAX_BIO = 1200;

    /**
     * {@code artist.getInfo} w danym jezyku. Pusty Optional = Last.fm nie odpowiedzial (albo nie ma klucza) - tego nie
     * zapamietujemy; "nie ma takiego wykonawcy" to odpowiedz bez opisu.
     */
    public Optional<ArtistInfo> artistInfo(String artistName, String lang) {
        if (!available() || artistName == null || artistName.isBlank()) {
            return Optional.empty();
        }
        try {
            UriComponentsBuilder b = UriComponentsBuilder.fromUriString(api)
                .queryParam("method", "artist.getinfo")
                .queryParam("artist", artistName)
                .queryParam("api_key", key)
                .queryParam("format", "json")
                .queryParam("autocorrect", 1);
            if (lang != null && !"en".equals(lang)) {
                b.queryParam("lang", lang);
            }
            JsonNode response = restClient.get().uri(b.build().toUriString()).retrieve().body(JsonNode.class);
            if (response == null) {
                return Optional.empty();
            }
            if (response.has("error")) {
                return response.path("error").asInt() == LASTFM_BRAK_ARTYSTY
                    ? Optional.of(new ArtistInfo(artistName, null, null, null, List.of()))
                    : Optional.empty();
            }
            JsonNode a = response.path("artist");
            if (a.isMissingNode()) {
                log.warn("Last.fm artist.getInfo: nie rozpoznano odpowiedzi dla '{}'", artistName);
                return Optional.empty();
            }
            List<String> podobni = new ArrayList<>();
            for (JsonNode s : a.path("similar").path("artist")) {
                String n = text(s, "name");
                if (n != null && !n.isBlank() && podobni.size() < 5) {
                    podobni.add(n.strip());
                }
            }
            String nazwa = text(a, "name");
            String adres = text(a, "url");
            return Optional.of(new ArtistInfo(
                nazwa == null || nazwa.isBlank() ? artistName : nazwa.strip(),
                bioText(a.path("bio").path("summary").asText(null)),
                adres != null && adres.startsWith("https://www.last.fm/") && adres.length() <= 500 ? adres : null,
                listeners(a.path("stats").path("listeners").asText(null)),
                podobni));
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac opisu '{}': {}", artistName, bezKlucza(e.getMessage()));
            return Optional.empty();
        }
    }

    private static Long listeners(String value) {
        try {
            return value == null ? null : Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Streszczenie z Last.fm to HTML z odnosnikiem "Read more on Last.fm" na koncu. Zostawiamy zwykly tekst: bez
     * odnosnika, bez znacznikow, z rozkodowanymi encjami i z jedna spacja zamiast odstepow. Za dlugi - ucinany na
     * granicy slowa. Pusty = null.
     */
    static String bioText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        // Znaczniki blokowe (akapit, nowy wiersz, punkt) dziela slowa; pozostale (<b>, <a>...) stoja w srodku zdania
        // i znikaja bez sladu - inaczej "w <b>Warszawie</b>." dawalo "Warszawie ."
        String bezZnacznikow = html.replaceAll("(?is)<a\\b[^>]*>[^<]*last\\.fm[^<]*</a>", " ")
            .replaceAll("(?i)<(br|/?p|/?div|/?li|/?ul|/?ol|/?h[1-6]|/?blockquote|/?tr|/?td)\\b[^>]*>", " ")
            .replaceAll("(?s)<[^>]*>", "");
        // Encje (&amp;, &oacute;, &#322;...) dopiero po zdjeciu znacznikow - "&lt;b&gt;" zostaje tekstem "<b>"
        String t = org.springframework.web.util.HtmlUtils.htmlUnescape(bezZnacznikow)
            .replace('\u00a0', ' ')
            .replaceAll("\\s+", " ")
            .strip();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > MAX_BIO) {
            int spacja = t.lastIndexOf(' ', MAX_BIO - 1);
            t = t.substring(0, spacja > MAX_BIO / 2 ? spacja : MAX_BIO - 1).stripTrailing() + "…";
        }
        return t;
    }

    private String bezKlucza(String tekst) {
        return tekst == null || key.isEmpty() ? tekst : tekst.replace(key, "***");
    }

    /** Wspolna czesc obu zapytan o historie sluchania. */
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
            log.warn("Last.fm nie odpowiedzial ({}): {}", method, bezKlucza(e.getMessage()));
            throw new IllegalStateException("Last.fm nie odpowiada", e);
        }

        /* Last.fm zwraca bledy POLEM "error" w tresci odpowiedzi. */
        if (response == null || response.has("error")) {
            log.warn("Last.fm odmowil ({}): {}", method,
                response == null ? "pusta odpowiedz" : response.get("message"));
            throw new IllegalArgumentException("Last.fm nie zna uzytkownika: " + user);
        }
        return response;
    }

    /** Wchodzi w zagniezdzona tablice odpowiedzi. */
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
