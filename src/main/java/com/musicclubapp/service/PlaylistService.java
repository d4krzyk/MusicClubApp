package com.musicclubapp.service;

import com.musicclubapp.dto.PlaylistResponse;
import com.musicclubapp.dto.PlaylistsResponse;
import com.musicclubapp.entity.FavoritePlaylist;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.music.MusicEmbed;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gablotka playlist na profilu: do pieciu skladanek "posluchaj tego, co ja".
 *
 * <p><b>Dlaczego to nie jest czesc {@code FavoritesService}.</b> Tamten serwis
 * pilnuje jednej twardej zasady: do ulubionych trafia wylacznie to, co istnieje
 * w katalogu Deezera - bo na tych danych opiera sie dopasowywanie ludzi.
 * Playlista nie moze przejsc przez katalog Deezera (skladanka z YouTube Music
 * nie ma tam odpowiednika) i <b>celowo nie liczy sie do zadnego dopasowania</b>.
 * Wciskanie tego do tamtej klasy oznaczaloby wyjatek od jej jedynej zasady,
 * a wyjatek od zasady to najkrotsza droga do tego, zeby przestala obowiazywac.</p>
 *
 * <p><b>Skad biora sie dane.</b> Z adresu - dokladnie tak samo jak przy postach:
 * {@code MusicLinkParser} rozklada go na serwis i identyfikator,
 * {@code MusicMetadataService} pobiera tytul i okladke (raz, przy dodawaniu),
 * a {@code MusicEmbed} sklada adres odtwarzacza. Zadnej z tych rzeczy nie
 * przyjmujemy od uzytkownika.</p>
 */
@Service
public class PlaylistService {

    private final FavoritePlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final MusicMetadataService musicMetadata;

    public PlaylistService(FavoritePlaylistRepository playlistRepository,
                           UserRepository userRepository,
                           MusicMetadataService musicMetadata) {
        this.playlistRepository = playlistRepository;
        this.userRepository = userRepository;
        this.musicMetadata = musicMetadata;
    }

    /**
     * Gablotka danej osoby.
     *
     * @param owner  czyj profil ogladamy
     * @param viewer kto oglada - od tego zalezy, czy widac przyciski edycji
     */
    @Transactional(readOnly = true)
    public PlaylistsResponse playlists(String owner, String viewer) {
        // Nieistniejacy uzytkownik ma dac 404, a nie pusta gablotke -
        // to dwie rozne odpowiedzi na dwa rozne pytania
        find(owner);

        List<PlaylistResponse> items =
            playlistRepository.findByOwnerUsernameOrderByPositionAsc(owner).stream()
                .map(this::toResponse)
                .toList();

        return new PlaylistsResponse(items, owner.equals(viewer),
            FavoritePlaylist.MAX_PER_USER);
    }

    /**
     * Dodaje playliste na koniec gablotki.
     *
     * <p>Kolejnosc sprawdzen jest celowa: <b>najpierw limit, potem adres</b>.
     * Przy pelnej gablotce nie ma sensu isc do serwisu po tytul nagrania,
     * ktorego i tak nie zapiszemy.</p>
     */
    @Transactional
    public PlaylistsResponse add(String username, String url) {
        User user = find(username);

        if (playlistRepository.countByOwnerUsername(username) >= FavoritePlaylist.MAX_PER_USER) {
            throw OperationNotAllowedException.playlistLimit();
        }

        ParsedMusicLink link = MusicLinkParser.parse(url)
            .orElseThrow(OperationNotAllowedException::notAPlaylist);

        /*
         * Rodzaj MUSI sie zgadzac. Link do pojedynczego utworu wyglada bardzo
         * podobnie i to najczestsza pomylka - a wpuszczony tutaj zrobilby
         * z gablotki playlist gablotke czegokolwiek.
         */
        if (link.kind() != MusicKind.PLAYLIST) {
            throw OperationNotAllowedException.notAPlaylist();
        }

        playlistRepository
            .findByOwnerUsernameAndProviderAndExternalId(
                username, link.provider(), link.externalId())
            .ifPresent(existing -> {
                throw OperationNotAllowedException.playlistAlreadyThere();
            });

        MusicMetadataService.Metadata metadata = musicMetadata.fetch(link);

        playlistRepository.save(new FavoritePlaylist(
            user,
            link.provider(),
            link.externalId(),
            metadata.title(),
            metadata.thumbnailUrl(),
            nextPosition(username)));

        return playlists(username, username);
    }

    /**
     * Usuwa playliste z wlasnej gablotki.
     *
     * <p><b>Sprawdzamy wlasciciela</b>, mimo ze login bierzemy z sesji.
     * Identyfikatory sa kolejnymi liczbami, wiec bez tego wystarczyloby zgadnac
     * numer, zeby usunac komus pozycje z profilu.</p>
     */
    @Transactional
    public PlaylistsResponse remove(String username, Long id) {
        FavoritePlaylist playlist = playlistRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("playlist", id));

        if (!playlist.getOwner().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesPost();
        }

        playlistRepository.delete(playlist);

        /*
         * flush() wypycha skasowanie do bazy PRZED przenumerowaniem. Bez tego
         * zapytanie nizej moglo by jeszcze zobaczyc usuwany wiersz i nadac mu
         * numer - a on i tak zaraz znika.
         */
        playlistRepository.flush();
        renumber(username);

        return playlists(username, username);
    }

    // ----------------------------------------------------------------------

    /**
     * Porzadkuje numery miejsc po usunieciu.
     *
     * <p>Bez tego po skasowaniu srodkowej pozycji zostalyby numery 0, 1, 3, 4 -
     * gablotka wygladalaby tak samo, ale kolejna dodana playlista dostalaby
     * numer 5 przy czterech pozycjach na ekranie. Dziury same z siebie nie
     * szkodza; szkodzi to, ze przestaja sie zgadzac z tym, co widac.</p>
     */
    private void renumber(String username) {
        int position = 0;
        for (FavoritePlaylist playlist
                : playlistRepository.findByOwnerUsernameOrderByPositionAsc(username)) {
            playlist.setPosition(position++);
        }
    }

    private int nextPosition(String username) {
        return (int) playlistRepository.countByOwnerUsername(username);
    }

    private PlaylistResponse toResponse(FavoritePlaylist playlist) {
        return new PlaylistResponse(
            playlist.getId(),
            playlist.getProvider(),
            playlist.getTitle(),
            playlist.getThumbnailUrl(),
            MusicEmbed.embedUrl(
                playlist.getProvider(), MusicKind.PLAYLIST, playlist.getExternalId(), null),
            MusicEmbed.canonicalUrl(
                playlist.getProvider(), MusicKind.PLAYLIST, playlist.getExternalId()));
    }

    private User find(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
