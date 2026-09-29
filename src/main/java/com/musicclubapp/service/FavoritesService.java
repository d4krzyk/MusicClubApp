package com.musicclubapp.service;

import com.musicclubapp.dto.ImportSummary;
import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import com.musicclubapp.dto.FavoritesResponse;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.Track;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.TrackRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Ulubieni artysci i utwory na profilu. */
@Service
public class FavoritesService {

    private static final Logger log = LoggerFactory.getLogger(FavoritesService.class);

    /** Ile pozycji z Last.fm bierzemy pod uwage przy jednym imporcie. */
    private static final int ILE_Z_LASTFM = 15;

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final DeezerCatalogService catalog;
    private final LastFmService lastFm;

    private final int maxArtists;
    private final int maxTracks;

    public FavoritesService(UserRepository userRepository,
                            ArtistRepository artistRepository,
                            TrackRepository trackRepository,
                            DeezerCatalogService catalog,
                            LastFmService lastFm,
                            @Value("${app.favorites.max-artists:30}") int maxArtists,
                            @Value("${app.favorites.max-tracks:30}") int maxTracks) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.catalog = catalog;
        this.lastFm = lastFm;
        this.maxArtists = maxArtists;
        this.maxTracks = maxTracks;
    }

    /** Ulubione danej osoby - do pokazania na jej profilu. */
    @Transactional(readOnly = true)
    public FavoritesResponse favorites(String owner, String viewer) {
        User user = find(owner);

        List<CatalogArtist> artists = user.getFavoriteArtists().stream()
            .map(a -> new CatalogArtist(a.getExternalId(), a.getName(), a.getImageUrl()))
            .toList();

        List<CatalogTrack> tracks = user.getFavoriteTracks().stream()
            .map(u -> new CatalogTrack(u.getExternalId(), u.getTitle(),
                u.getArtistName(), u.getArtistExternalId(), u.getImageUrl()))
            .toList();

        // O tym, czy widac przyciski edycji, decyduje SERWER
        boolean editable = owner.equals(viewer);

        return new FavoritesResponse(artists, tracks, editable, maxArtists, maxTracks);
    }

    // =========================================================================
    //  Artysci
    // =========================================================================

    @Transactional
    public FavoritesResponse addArtist(String username, String externalId) {
        User user = find(username);

        if (user.getFavoriteArtists().size() >= maxArtists) {
            throw OperationNotAllowedException.favoritesLimit();
        }

        Artist artist = loadOrFetchArtist(externalId)
            .orElseThrow(OperationNotAllowedException::notInCatalog);

        user.getFavoriteArtists().add(artist);
        return favorites(username, username);
    }

    @Transactional
    public FavoritesResponse removeArtist(String username, String externalId) {
        User user = find(username);
        /* Kasujemy tylko POWIAZANIE, a nie samego artyste. */
        user.getFavoriteArtists().removeIf(a -> a.getExternalId().equals(externalId));
        return favorites(username, username);
    }

    // =========================================================================
    //  Utwory
    // =========================================================================

    @Transactional
    public FavoritesResponse addTrack(String username, String externalId) {
        User user = find(username);

        if (user.getFavoriteTracks().size() >= maxTracks) {
            throw OperationNotAllowedException.favoritesLimit();
        }

        Track track = loadOrFetchTrack(externalId)
            .orElseThrow(OperationNotAllowedException::notInCatalog);

        user.getFavoriteTracks().add(track);
        return favorites(username, username);
    }

    @Transactional
    public FavoritesResponse removeTrack(String username, String externalId) {
        User user = find(username);
        user.getFavoriteTracks().removeIf(u -> u.getExternalId().equals(externalId));
        return favorites(username, username);
    }

    // =========================================================================
    //  Import z Last.fm
    // =========================================================================

    /** Czy przycisk importu ma sie w ogole pokazac (czy jest klucz do Last.fm). */
    public boolean importAvailable() {
        return lastFm.available();
    }

    /** Pobiera najczesciej sluchanych artystow i utwory z Last.fm i dopisuje je do ulubionych. */
    @Transactional
    public ImportSummary importFromLastFm(String username, String lastFmUsername) {
        if (!lastFm.available()) {
            throw OperationNotAllowedException.lastFmDisabled();
        }

        User user = find(username);

        List<String> artistNames;
        List<LastFmService.ListenedTrack> listenedTracks;
        try {
            artistNames = lastFm.topArtists(lastFmUsername, ILE_Z_LASTFM);
            listenedTracks = lastFm.topTracks(lastFmUsername, ILE_Z_LASTFM);
        } catch (IllegalArgumentException e) {
            // Literowka w nazwie - o tym uzytkownik musi sie dowiedziec
            throw OperationNotAllowedException.lastFmUnknownUser();
        } catch (IllegalStateException e) {
            // Awaria po stronie Last.fm - to nie jest wina uzytkownika
            throw OperationNotAllowedException.lastFmUnavailable();
        }

        int addedArtists = 0;
        int addedTracks = 0;
        int skipped = 0;
        int alreadyPresent = 0;
        boolean limit = false;

        for (String name : artistNames) {
            if (user.getFavoriteArtists().size() >= maxArtists) {
                limit = true;
                break;
            }
            Optional<CatalogArtist> found = catalog.findArtist(name);
            if (found.isEmpty()) {
                skipped++;
                continue;
            }
            Artist artist = loadOrFetchArtist(found.get().externalId()).orElse(null);
            if (artist == null) {
                skipped++;
            } else if (user.getFavoriteArtists().add(artist)) {
                addedArtists++;
            } else {
                alreadyPresent++;
            }
        }

        for (LastFmService.ListenedTrack listened : listenedTracks) {
            if (user.getFavoriteTracks().size() >= maxTracks) {
                limit = true;
                break;
            }
            Optional<CatalogTrack> found =
                catalog.findTrack(listened.artistName(), listened.title());
            if (found.isEmpty()) {
                skipped++;
                continue;
            }
            Track track = loadOrFetchTrack(found.get().externalId()).orElse(null);
            if (track == null) {
                skipped++;
            } else if (user.getFavoriteTracks().add(track)) {
                addedTracks++;
            } else {
                alreadyPresent++;
            }
        }

        log.info("Import z Last.fm dla '{}': +{} artystow, +{} utworow, {} pominietych",
            username, addedArtists, addedTracks, skipped);

        return new ImportSummary(addedArtists, addedTracks, skipped, alreadyPresent, limit);
    }

    // =========================================================================
    //  Wspolne
    // =========================================================================

    /** Znajduje artyste w naszej bazie albo sciaga go z Deezera i zapisuje. */
    private Optional<Artist> loadOrFetchArtist(String externalId) {
        Optional<Artist> fromDatabase = artistRepository.findByExternalId(externalId);
        if (fromDatabase.isPresent()) {
            return fromDatabase;
        }

        return catalog.fetchArtist(externalId).map(fromCatalog -> {
            Artist created = new Artist(
                fromCatalog.externalId(), fromCatalog.name(), fromCatalog.imageUrl());

            /*
             * Gatunki przy pierwszym zapisie artysty. Gdy Last.fm nie odpowie
             * albo nie ma klucza, artysta zostaje "niesprawdzony" - gatunki
             * dociagnie pozniej ArtistGenreBackfillService. Wczesniej zostawal
             * bez gatunkow na zawsze.
             */
            lastFm.lookupArtistGenres(fromCatalog.name())
                .ifPresent(genres -> created.genresChecked(genres, LocalDateTime.now()));

            return artistRepository.save(created);
        });
    }

    private Optional<Track> loadOrFetchTrack(String externalId) {
        Optional<Track> fromDatabase = trackRepository.findByExternalId(externalId);
        if (fromDatabase.isPresent()) {
            return fromDatabase;
        }

        return catalog.fetchTrack(externalId).map(fromCatalog -> trackRepository.save(new Track(
            fromCatalog.externalId(),
            fromCatalog.title(),
            fromCatalog.artistName(),
            fromCatalog.artistExternalId(),
            fromCatalog.imageUrl())));
    }

    private User find(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("Uzytkownik", username));
    }
}
