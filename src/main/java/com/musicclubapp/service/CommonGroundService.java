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

/**
 * <b>Co laczy dwie konkretne osoby.</b>
 *
 * <p>Aplikacja od poczatku umiala policzyc, ILE ktos ma z kim wspolnego -
 * na tym opiera sie kolejnosc proponowanych znajomych. Nie umiala natomiast
 * powiedziec, CO to jest, a to wlasnie ta druga rzecz daje powod, zeby
 * napisac do obcej osoby. Ten serwis odpowiada na to drugie pytanie.</p>
 *
 * <p><b>Dlaczego osobny serwis, a nie metoda w {@code FriendService}.</b>
 * Tamten zajmuje sie relacja: zaproszeniami, przyjmowaniem, usuwaniem
 * znajomosci - czyli tym, co ludzie sobie <i>robia</i>. Tutaj nikt nikomu nic
 * nie robi; to czysty odczyt porownujacy dwa profile i uzywany takze wtedy,
 * gdy zadna relacja nie istnieje.</p>
 *
 * <p><b>Czesc wspolna liczymy w Javie, a nie w SQL-u</b> - inaczej niz przy
 * proponowanych znajomych. Tam trzeba bylo policzyc dopasowanie dla
 * <i>wszystkich</i> uzytkownikow naraz i tylko baza mogla to zrobic sensownie.
 * Tutaj chodzi o dwie osoby i najwyzej po kilkadziesiat pozycji na liscie -
 * zapytanie z podwojnym zlaczeniem byloby trudniejsze do przeczytania, a nie
 * szybsze.</p>
 */
@Service
public class CommonGroundService {

    private final UserRepository userRepository;

    public CommonGroundService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param viewer kto oglada
     * @param whose  czyj profil oglada
     */
    @Transactional(readOnly = true)
    public CommonGroundResponse between(String viewer, String whose) {
        /*
         * Wlasny profil sprawdzamy PRZED pobraniem czegokolwiek. Bez tego
         * wynikiem bylaby cala wlasna lista ulubionych opisana jako "co Was
         * laczy" - technicznie prawdziwe, w interfejsie bez sensu.
         */
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
         * Gatunki bierzemy zapytaniem, a nie z encji artystow - inaczej
         * doczytanie ich kosztowaloby jedno zapytanie NA KAZDEGO ulubionego
         * wykonawce obu osob.
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
