package com.musicclubapp.mapper;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicEmbed;
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
     * @param ogladajacy zalogowany uzytkownik (moze byc {@code null} - wtedy
     *                   nikt nie moze nic kasowac)
     * @param reakcje    policzone reakcje tego posta; dla swiezo utworzonego
     *                   wpisu podaj {@link ReactionSummary#pusta()}
     */
    public PostResponse toResponse(Post post, User ogladajacy, ReactionSummary reakcje) {
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
            post.getMusicProvider(),
            post.getMusicKind(),
            post.getMusicTitle(),
            post.getMusicThumbnailUrl(),
            post.getMusicStartSeconds(),
            adresStrony(post),
            post.getCreatedAt(),
            czyMozeUsunac(post, ogladajacy),
            czyMozeEdytowac(post, ogladajacy),
            reakcje);
    }

    private String adresAvatara(User user) {
        return user.getAvatarFileName() == null
            ? null
            : SCIEZKA_PLIKOW + user.getAvatarFileName();
    }

    /**
     * Gotowy adres do {@code <iframe>}.
     *
     * <p>Sklada go {@link MusicEmbed}, bo kazdy serwis robi to inaczej.
     * Frontend dostaje wynik i nie musi znac zadnego z tych formatow -
     * dolozenie trzeciego serwisu nie wymaga wtedy ruszania Reacta.</p>
     */
    private String adresOdtwarzacza(Post post) {
        if (!post.maMuzyke()) {
            return null;
        }
        return MusicEmbed.adresOsadzenia(
            post.getMusicProvider(),
            post.getMusicKind(),
            post.getMusicExternalId(),
            post.getMusicStartSeconds());
    }

    /** Adres strony w serwisie - do formularza edycji i przycisku "otworz". */
    private String adresStrony(Post post) {
        if (!post.maMuzyke()) {
            return null;
        }
        return MusicEmbed.adresZwykly(
            post.getMusicProvider(), post.getMusicKind(), post.getMusicExternalId());
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
