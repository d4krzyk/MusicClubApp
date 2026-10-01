package com.musicclubapp.service;

import com.musicclubapp.dto.EventReasonKind;
import com.musicclubapp.dto.EventReasonResponse;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.Track;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.EventFeatureRow;
import com.musicclubapp.repository.EventPerformerRow;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Dla ciebie": ktore koncerty pasuja do gustu z profilu i dlaczego.
 *
 * Kazde wydarzenie dostaje punkty za to, co ma wspolnego z profilem:
 *
 *   ulubiony artysta w skladzie       100  (najwyzej dwoch)
 *   wykonawca ulubionego utworu        70  (najwyzej dwoch)
 *   ten sam gatunek co u ciebie        15  (najwyzej trzy)
 *   ta sama rodzina gatunkow            8  (najwyzej dwie, np. "hip-hop")
 *   znajomy zapisany na wydarzenie     20  (najwyzej trzech)
 *
 * Kolejnosc wag jest celowa: koncert kogos, kogo sluchasz, jest zawsze
 * wyzej niz koncert "w twoim klimacie". Przy rownej liczbie punktow wyzej
 * jest to, co blizej w czasie.
 *
 * Przy kazdym wydarzeniu zostaje lista powodow - lista "Dla ciebie" bez
 * wyjasnienia, czemu cos na niej jest, wyglada jak przypadkowa.
 */
@Service
public class EventMatchService {

    static final int ZA_ARTYSTE = 100;
    static final int ZA_UTWOR = 70;
    static final int ZA_GATUNEK = 15;
    static final int ZA_RODZINE = 8;
    static final int ZA_ZNAJOMEGO = 20;

    /** Ile punktow za kazdy poziom bliskosci (0-5, patrz LocationScore): to samo miasto = 40. */
    static final int ZA_BLISKOSC = 8;

    private final UserRepository userRepository;
    private final MusicEventRepository eventRepository;
    private final PerformerTagService performerTags;
    private final LocationService location;

    public EventMatchService(UserRepository userRepository,
                             MusicEventRepository eventRepository,
                             PerformerTagService performerTags,
                             LocationService location) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.performerTags = performerTags;
        this.location = location;
    }

    /** Gust jednej osoby w postaci gotowej do porownywania. */
    public record Taste(
        /* klucz nazwy -> nazwa do pokazania */
        Map<String, String> artists,
        /* klucz nazwy wykonawcy -> tytul ulubionego utworu */
        Map<String, String> trackArtists,
        /* znormalizowany tag -> jak go pokazac i ilu ulubionych artystow go ma */
        Map<String, GenreWeight> genres,
        Set<String> families,
        Set<Long> friendIds
    ) {

        public boolean empty() {
            return artists.isEmpty() && trackArtists.isEmpty() && genres.isEmpty();
        }
    }

    public record GenreWeight(String display, int count) { }

    /** Wynik dopasowania jednego wydarzenia. */
    public record Match(int score, List<EventReasonResponse> reasons) {

        static final Match NONE = new Match(0, List.of());
    }

    /** Jedna karta listy: wydarzenie, ile ma jeszcze terminow, czemu pasuje i jak daleko jest (km, null = nie wiadomo). */
    public record Ranked(Long eventId, long moreDates, Match match, Double distanceKm) { }

    @Transactional(readOnly = true)
    public Taste tasteOf(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return new Taste(Map.of(), Map.of(), Map.of(), Set.of(), Set.of());
        }

        Map<String, String> artists = new LinkedHashMap<>();
        for (Artist a : user.getFavoriteArtists()) {
            artists.putIfAbsent(NameKeys.of(a.getName()), a.getName());
        }

        Map<String, String> trackArtists = new LinkedHashMap<>();
        for (Track t : user.getFavoriteTracks()) {
            trackArtists.putIfAbsent(NameKeys.of(t.getArtistName()), t.getTitle());
        }
        artists.remove("");
        trackArtists.remove("");

        /* Tag, ktory ma kilku moich artystow, jest bardziej "moj" niz taki, ktory ma jeden. */
        Map<String, GenreWeight> genres = new HashMap<>();
        for (String raw : userRepository.genreTagsOfFavorites(username)) {
            for (String tag : GenreTags.tags(raw)) {
                GenreWeight before = genres.get(tag);
                genres.put(tag, new GenreWeight(
                    before == null ? raw.toLowerCase(Locale.ROOT) : before.display(),
                    before == null ? 1 : before.count() + 1));
            }
        }

        return new Taste(artists, trackArtists, genres, GenreTags.families(genres.keySet()),
            new HashSet<>(userRepository.friendIdsOf(username)));
    }

    /**
     * Wszystkie nadchodzace wydarzenia, ktore do czegos pasuja, od najlepiej
     * pasujacych. Terminy tej samej serii sa zwiniete w jedna pozycje.
     *
     * <p>Bliskosc miasta ogladajacego ({@code origin}) tylko przesuwa kolejnosc - o samym tym, czy
     * wydarzenie "pasuje", decyduje gust. Przy {@code radiusKm > 0} wypadaja wydarzenia dalsze niz
     * promien albo bez znanego polozenia.</p>
     *
     * @param friendCounts ilu znajomych zapisalo sie na kazde wydarzenie
     */
    @Transactional(readOnly = true)
    public List<Ranked> rank(Taste taste, LocalDate today, String country, String cityKey, String phrase,
                             Map<Long, Long> friendCounts, LocationService.Origin origin, int radiusKm) {
        List<EventFeatureRow> features = eventRepository.upcomingFeatures(today, country);
        Map<Long, List<String>> performers =
            performersByEvent(eventRepository.upcomingPerformers(today, country));
        Map<String, Set<String>> tags = performerTags.tagsOf(allKeys(performers.values()));

        record Candidate(EventFeatureRow first, long moreDates, Match match, Double km, int total) { }
        List<Candidate> candidates = new ArrayList<>();
        for (List<EventFeatureRow> dates : seriesOf(features, performers, cityKey, phrase)) {
            EventFeatureRow first = dates.get(0);
            Double km = location.distanceKm(origin, first.getLatitude(), first.getLongitude(), first.getCityKey());
            if (outsideRadius(origin, radiusKm, km)) {
                continue;
            }
            long friends = dates.stream().mapToLong(d -> friendCounts.getOrDefault(d.getId(), 0L)).sum();
            Match match = match(taste, first, performers.getOrDefault(first.getId(), List.of()), tags, friends);
            if (match.score() > 0) {
                candidates.add(new Candidate(first, dates.size() - 1L, match, km,
                    match.score() + ZA_BLISKOSC * LocationScore.level(km)));
            }
        }

        candidates.sort(Comparator.<Candidate>comparingInt(c -> -c.total())
            .thenComparing(Candidate::first, PO_DACIE));

        return candidates.stream()
            .map(c -> new Ranked(c.first().getId(), c.moreDates(), c.match(), c.km()))
            .toList();
    }

    /**
     * Wydarzenia w promieniu {@code radiusKm} od miasta ogladajacego, od najblizszego terminu - widok
     * "Najblizsze" z ograniczeniem do okolicy. Seria jest jedna pozycja, tak jak wszedzie.
     */
    @Transactional(readOnly = true)
    public List<Ranked> within(LocalDate today, String country, String cityKey, String phrase,
                               LocationService.Origin origin, int radiusKm) {
        List<EventFeatureRow> features = eventRepository.upcomingFeatures(today, country);
        Map<Long, List<String>> performers = phrase == null || phrase.isBlank()
            ? Map.of()
            : performersByEvent(eventRepository.upcomingPerformers(today, country));

        List<Ranked> result = new ArrayList<>();
        List<EventFeatureRow> firsts = new ArrayList<>();
        Map<Long, Ranked> byId = new HashMap<>();
        for (List<EventFeatureRow> dates : seriesOf(features, performers, cityKey, phrase)) {
            EventFeatureRow first = dates.get(0);
            Double km = location.distanceKm(origin, first.getLatitude(), first.getLongitude(), first.getCityKey());
            if (outsideRadius(origin, radiusKm, km)) {
                continue;
            }
            firsts.add(first);
            byId.put(first.getId(), new Ranked(first.getId(), dates.size() - 1L, Match.NONE, km));
        }
        firsts.sort(PO_DACIE);
        for (EventFeatureRow f : firsts) {
            result.add(byId.get(f.getId()));
        }
        return result;
    }

    /** Czy wydarzenie odpada przez promien: tylko gdy promien jest ustawiony i znamy punkt wyjscia. */
    private static boolean outsideRadius(LocationService.Origin origin, int radiusKm, Double km) {
        return radiusKm > 0 && origin != null && (km == null || km > radiusKm);
    }

    /**
     * Terminy pogrupowane w serie (kazda od najblizszego terminu), po filtrze miasta i szukanego
     * tekstu. Najpierw filtry, potem zwijanie serii - jak na zwyklej liscie.
     */
    private static List<List<EventFeatureRow>> seriesOf(List<EventFeatureRow> features,
                                                        Map<Long, List<String>> performers,
                                                        String cityKey, String phrase) {
        String szukane = phrase == null ? "" : phrase.strip().toLowerCase(Locale.ROOT);
        Map<String, List<EventFeatureRow>> series = new LinkedHashMap<>();
        for (EventFeatureRow f : features) {
            if (!cityKey.isEmpty() && !cityKey.equals(f.getCityKey())) {
                continue;
            }
            if (!szukane.isEmpty() && !matchesPhrase(f, performers.getOrDefault(f.getId(), List.of()), szukane)) {
                continue;
            }
            series.computeIfAbsent(f.getSeriesKey(), k -> new ArrayList<>()).add(f);
        }
        series.values().forEach(dates -> dates.sort(PO_DACIE));
        return new ArrayList<>(series.values());
    }

    /** Dlaczego to jedno wydarzenie pasuje - na strone wydarzenia. */
    @Transactional(readOnly = true)
    public Match matchOne(Taste taste, Long eventId, List<String> eventPerformers, long friends) {
        return eventRepository.featuresOf(eventId)
            .map(f -> match(taste, f, eventPerformers,
                performerTags.tagsOf(eventPerformers.stream().map(NameKeys::of).toList()), friends))
            .orElse(Match.NONE);
    }

    /** Punkty i powody dla jednego wydarzenia. */
    static Match match(Taste taste, EventFeatureRow event, List<String> eventPerformers,
                       Map<String, Set<String>> performerTags, long friends) {
        int score = 0;
        List<EventReasonResponse> reasons = new ArrayList<>();

        Set<String> performerKeys = eventPerformers.stream().map(NameKeys::of).collect(Collectors.toSet());
        String nameKey = NameKeys.of(event.getName());

        /* 1. Ulubieni artysci w skladzie albo w nazwie wydarzenia */
        Set<String> matchedArtists = new LinkedHashSet<>();
        for (Map.Entry<String, String> a : taste.artists().entrySet()) {
            if (matchedArtists.size() < 2
                    && (performerKeys.contains(a.getKey()) || NameKeys.mentions(nameKey, a.getKey()))) {
                matchedArtists.add(a.getKey());
                score += ZA_ARTYSTE;
                reasons.add(new EventReasonResponse(EventReasonKind.ARTIST, a.getValue()));
            }
        }

        /* 2. Wykonawcy ulubionych utworow - o ile nie liczylismy ich juz jako artystow */
        int utwory = 0;
        for (Map.Entry<String, String> t : taste.trackArtists().entrySet()) {
            if (utwory < 2 && !matchedArtists.contains(t.getKey())
                    && (performerKeys.contains(t.getKey()) || NameKeys.mentions(nameKey, t.getKey()))) {
                utwory++;
                score += ZA_UTWOR;
                reasons.add(new EventReasonResponse(EventReasonKind.TRACK, t.getValue()));
            }
        }

        /* 3. Gatunki: etykiety Ticketmastera i tagi Last.fm wykonawcow */
        Set<String> eventTags = new LinkedHashSet<>();
        eventTags.addAll(GenreTags.tags(event.getGenre()));
        eventTags.addAll(GenreTags.tags(event.getSubGenre()));
        for (String key : performerKeys) {
            eventTags.addAll(performerTags.getOrDefault(key, Set.of()));
        }

        List<String> exact = taste.genres().keySet().stream()
            .filter(eventTags::contains)
            .sorted(Comparator.comparingInt((String tag) -> -taste.genres().get(tag).count())
                .thenComparing(Comparator.naturalOrder()))
            .toList();
        score += ZA_GATUNEK * Math.min(3, exact.size());

        Set<String> familiesOnly = new LinkedHashSet<>(GenreTags.families(eventTags));
        familiesOnly.retainAll(taste.families());
        familiesOnly.removeAll(GenreTags.families(new LinkedHashSet<>(exact)));
        score += ZA_RODZINE * Math.min(2, familiesOnly.size());

        if (!exact.isEmpty()) {
            reasons.add(new EventReasonResponse(EventReasonKind.GENRE, exact.stream()
                .limit(3)
                .map(tag -> taste.genres().get(tag).display())
                .collect(Collectors.joining(", "))));
        } else if (!familiesOnly.isEmpty()) {
            reasons.add(new EventReasonResponse(EventReasonKind.GENRE,
                familiesOnly.stream().limit(2).collect(Collectors.joining(", "))));
        }

        /* 4. Znajomi */
        if (friends > 0) {
            score += ZA_ZNAJOMEGO * (int) Math.min(3, friends);
            reasons.add(new EventReasonResponse(EventReasonKind.FRIENDS, String.valueOf(friends)));
        }

        return new Match(score, reasons);
    }

    private static final Comparator<EventFeatureRow> PO_DACIE = Comparator
        .comparing(EventFeatureRow::getStartDate)
        .thenComparing(EventFeatureRow::getStartTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
        .thenComparing(EventFeatureRow::getId);

    private static boolean matchesPhrase(EventFeatureRow f, List<String> performers, String phrase) {
        if (contains(f.getName(), phrase) || contains(f.getVenueName(), phrase)) {
            return true;
        }
        return performers.stream().anyMatch(p -> contains(p, phrase));
    }

    private static boolean contains(String text, String phrase) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(phrase);
    }

    private static Map<Long, List<String>> performersByEvent(List<EventPerformerRow> rows) {
        Map<Long, List<String>> result = new HashMap<>();
        for (EventPerformerRow row : rows) {
            result.computeIfAbsent(row.getEventId(), k -> new ArrayList<>()).add(row.getName());
        }
        return result;
    }

    private static Set<String> allKeys(Collection<List<String>> names) {
        return names.stream().flatMap(List::stream).map(NameKeys::of).collect(Collectors.toSet());
    }
}
