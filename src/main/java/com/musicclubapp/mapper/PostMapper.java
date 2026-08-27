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
    public static final String UPLOADS_PATH = "/uploads/";

    /**
     * @param ogladajacy zalogowany uzytkownik (moze byc {@code null} - wtedy
     *                   nikt nie moze nic kasowac)
     * @param reakcje    policzone reakcje tego posta; dla swiezo utworzonego
     *                   wpisu podaj {@link ReactionSummary#pusta()}
     */
    public PostResponse toResponse(Post post, User viewer, ReactionSummary reactions) {
        List<String> imageUrls = post.getImages().stream()
            .map(PostImage::getFileName)
            .map(name -> UPLOADS_PATH + name)
            .toList();

        return new PostResponse(
            post.getId(),
            post.getAuthor().getUsername(),
            avatarUrl(post.getAuthor()),
            post.getContent(),
            imageUrls,
            playerUrl(post),
            post.getMusicProvider(),
            post.getMusicKind(),
            post.getMusicTitle(),
            post.getMusicThumbnailUrl(),
            post.getMusicStartSeconds(),
            pageUrl(post),
            post.getCreatedAt(),
            canDelete(post, viewer),
            canEdit(post, viewer),
            reactions);
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : UPLOADS_PATH + user.getAvatarFileName();
    }

    /**
     * Gotowy adres do {@code <iframe>}.
     *
     * <p>Sklada go {@link MusicEmbed}, bo kazdy serwis robi to inaczej.
     * Frontend dostaje wynik i nie musi znac zadnego z tych formatow -
     * dolozenie trzeciego serwisu nie wymaga wtedy ruszania Reacta.</p>
     */
    private String playerUrl(Post post) {
        if (!post.hasMusic()) {
            return null;
        }
        return MusicEmbed.embedUrl(
            post.getMusicProvider(),
            post.getMusicKind(),
            post.getMusicExternalId(),
            post.getMusicStartSeconds());
    }

    /** Adres strony w serwisie - do formularza edycji i przycisku "otworz". */
    private String pageUrl(Post post) {
        if (!post.hasMusic()) {
            return null;
        }
        return MusicEmbed.canonicalUrl(
            post.getMusicProvider(), post.getMusicKind(), post.getMusicExternalId());
    }

    /** Edytowac moze WYLACZNIE autor - administrator moderuje usuwaniem. */
    private boolean canEdit(Post post, User viewer) {
        return viewer != null
            && post.getAuthor().getUsername().equals(viewer.getUsername());
    }

    /** Post moze skasowac jego autor albo administrator. */
    private boolean canDelete(Post post, User viewer) {
        if (viewer == null) {
            return false;
        }
        return viewer.getRole() == Role.ADMIN
            || post.getAuthor().getUsername().equals(viewer.getUsername());
    }
}
