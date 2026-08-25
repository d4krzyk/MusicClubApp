package com.musicclubapp.mapper;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Przepisuje encje {@link Post} na DTO wysylane do przegladarki.
 *
 * <p>Wyklad 4, slajd 23: mapper jako {@code @Component} wstrzykiwany do serwisu.</p>
 */
@Component
public class PostMapper {

    /** Publiczna sciezka, pod ktora serwer wystawia wgrane pliki. */
    public static final String SCIEZKA_PLIKOW = "/uploads/";

    /**
     * Adres odtwarzacza Spotify.
     *
     * <p>Zwykly link {@code open.spotify.com/track/...} nie wyswietli sie
     * w ramce - Spotify blokuje osadzanie zwyklych stron. Do tego sluzy
     * osobny adres z czlonem {@code /embed/}.</p>
     */
    private static final String SPOTIFY_EMBED = "https://open.spotify.com/embed/track/";

    /** Zwykly adres utworu - wstawiamy go w formularz edycji. */
    private static final String SPOTIFY_TRACK = "https://open.spotify.com/track/";

    /**
     * @param ogladajacy    zalogowany uzytkownik (moze byc {@code null} - wtedy
     *                      nikt nie moze nic kasowac)
     */
    public PostResponse toResponse(Post post, User ogladajacy) {
        List<String> adresyZdjec = post.getImages().stream()
            .map(PostImage::getFileName)
            .map(nazwa -> SCIEZKA_PLIKOW + nazwa)
            .toList();

        return new PostResponse(
            post.getId(),
            post.getAuthor().getUsername(),
            adresAvatara(post.getAuthor()),
            post.getContent(),
            adresyZdjec,
            adresOdtwarzacza(post),
            post.getSpotifyStartSeconds(),
            post.getCreatedAt(),
            czyMozeUsunac(post, ogladajacy),
            czyMozeEdytowac(post, ogladajacy),
            post.getSpotifyTrackId() == null ? null : SPOTIFY_TRACK + post.getSpotifyTrackId());
    }

    private String adresAvatara(User user) {
        return user.getAvatarFileName() == null
            ? null
            : SCIEZKA_PLIKOW + user.getAvatarFileName();
    }

    /**
     * Sklada adres odtwarzacza z zapisanego identyfikatora utworu.
     *
     * <p>Parametr {@code t=<sekundy>} ustawia moment, od ktorego zaczyna sie
     * odtwarzanie - odtwarzacz pokazuje go na pasku postepu.</p>
     */
    private String adresOdtwarzacza(Post post) {
        if (post.getSpotifyTrackId() == null) {
            return null;
        }

        String adres = SPOTIFY_EMBED + post.getSpotifyTrackId();

        Integer start = post.getSpotifyStartSeconds();
        return (start != null && start > 0) ? adres + "?t=" + start : adres;
    }

    /** Edytowac moze WYLACZNIE autor - administrator moderuje usuwaniem. */
    private boolean czyMozeEdytowac(Post post, User ogladajacy) {
        return ogladajacy != null
            && post.getAuthor().getUsername().equals(ogladajacy.getUsername());
    }

    /** Post moze skasowac jego autor albo administrator. */
    private boolean czyMozeUsunac(Post post, User ogladajacy) {
        if (ogladajacy == null) {
            return false;
        }
        return ogladajacy.getRole() == Role.ADMIN
            || post.getAuthor().getUsername().equals(ogladajacy.getUsername());
    }
}
