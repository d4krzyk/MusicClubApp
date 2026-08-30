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

/** Gablotka playlist na profilu: do pieciu skladanek "posluchaj tego, co ja". */
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

    /** Gablotka danej osoby. */
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

    /** Dodaje playliste na koniec gablotki. */
    @Transactional
    public PlaylistsResponse add(String username, String url) {
        User user = find(username);

        if (playlistRepository.countByOwnerUsername(username) >= FavoritePlaylist.MAX_PER_USER) {
            throw OperationNotAllowedException.playlistLimit();
        }

        ParsedMusicLink link = MusicLinkParser.parse(url)
            .orElseThrow(OperationNotAllowedException::notAPlaylist);

        /* Rodzaj MUSI sie zgadzac. */
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

    /** Usuwa playliste z wlasnej gablotki. */
    @Transactional
    public PlaylistsResponse remove(String username, Long id) {
        FavoritePlaylist playlist = playlistRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("playlist", id));

        if (!playlist.getOwner().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesPost();
        }

        playlistRepository.delete(playlist);

        /* flush() wypycha skasowanie do bazy PRZED przenumerowaniem. */
        playlistRepository.flush();
        renumber(username);

        return playlists(username, username);
    }

    // ----------------------------------------------------------------------

    /** Porzadkuje numery miejsc po usunieciu. */
    private void renumber(String username) {
        int position = 0;
        for (FavoritePlaylist playlist
                : playlistRepository.findByOwnerUsernameOrderByPositionAsc(username)) {
            playlist.setPosition(position++);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Kasuje gablotke playlist konta - przy usuwaniu uzytkownika. */
    @Transactional
    public void deleteAllOf(Long ownerId) {
        playlistRepository.deleteByOwnerId(ownerId);
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

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
