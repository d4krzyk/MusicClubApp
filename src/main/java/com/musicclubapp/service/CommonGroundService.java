package com.musicclubapp.service;

import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import com.musicclubapp.dto.CommonGroundResponse;
import com.musicclubapp.dto.PersonCard;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Co laczy dwie konkretne osoby. */
@Service
public class CommonGroundService {

    private final UserRepository userRepository;

    public CommonGroundService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CommonGroundResponse between(String viewer, String whose) {
        /* Wlasny profil sprawdzamy PRZED pobraniem czegokolwiek. */
        if (viewer.equals(whose)) {
            return CommonGroundResponse.ownProfile();
        }

        User me = find(viewer);
        User other = find(whose);

        Set<String> myArtists = externalIds(me.getFavoriteArtists().stream()
            .map(a -> a.getExternalId()).toList());
        Set<String> myTracks = externalIds(me.getFavoriteTracks().stream()
            .map(u -> u.getExternalId()).toList());
        Set<Long> myFriends = me.getFriends().stream().map(User::getId)
            .collect(java.util.stream.Collectors.toSet());

        List<CatalogArtist> artists = other.getFavoriteArtists().stream()
            .filter(a -> myArtists.contains(a.getExternalId()))
            .map(a -> new CatalogArtist(a.getExternalId(), a.getName(), a.getImageUrl()))
            .toList();

        List<CatalogTrack> tracks = other.getFavoriteTracks().stream()
            .filter(u -> myTracks.contains(u.getExternalId()))
            .map(u -> new CatalogTrack(u.getExternalId(), u.getTitle(),
                u.getArtistName(), u.getArtistExternalId(), u.getImageUrl()))
            .toList();

        /*
         * Gatunki bierzemy zapytaniem, a nie z encji artystow - inaczej doczytanie ich
         * kosztowaloby jedno zapytanie NA KAZDEGO ulubionego wykonawce obu osob.
         */
        Set<String> myGenres = new HashSet<>(userRepository.genresOf(viewer));
        List<String> genres = userRepository.genresOf(whose).stream()
            .filter(myGenres::contains)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        List<PersonCard> friends = other.getFriends().stream()
            .filter(u -> myFriends.contains(u.getId()))
            .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
            .map(u -> new PersonCard(u.getUsername(), avatarUrl(u)))
            .toList();

        return new CommonGroundResponse(artists, tracks, genres, friends, false);
    }

    private Set<String> externalIds(List<String> ids) {
        return new HashSet<>(ids);
    }

    /** Baza trzyma nazwe pliku, na zewnatrz idzie gotowy adres - jak wszedzie. */
    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }

    private User find(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
