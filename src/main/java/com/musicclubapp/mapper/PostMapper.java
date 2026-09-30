package com.musicclubapp.mapper;

import com.musicclubapp.dto.PostEventRef;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicEmbed;
import org.springframework.stereotype.Component;

import java.util.List;

/** Przepisuje encje Post na DTO wysylane do przegladarki. */
@Component
public class PostMapper {

    /** Publiczna sciezka, pod ktora serwer wystawia wgrane pliki. */
    public static final String UPLOADS_PATH = "/uploads/";

    /** Wersja dla pojedynczego posta. */
    public PostResponse toResponse(Post post, User viewer, ReactionSummary reactions) {
        return toResponse(post, viewer, reactions, inCircle(post, viewer));
    }

    public PostResponse toResponse(Post post, User viewer, ReactionSummary reactions,
                                   boolean fromFriend) {
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
            reactions,
            post.getVisibility(),
            fromFriend,
            eventRef(post.getEvent()),
            ClanMapper.badge(post.getClan()),
            null);
    }

    private PostEventRef eventRef(MusicEvent event) {
        return event == null
            ? null
            : new PostEventRef(event.getId(), event.getName(), event.getStartDate(),
                event.getCityKey(), event.getCity());
    }

    /** Czy autor posta to ogladajacy albo ktos z jego znajomych. */
    private boolean inCircle(Post post, User viewer) {
        if (viewer == null) {
            return false;
        }
        return post.getAuthor().getId().equals(viewer.getId())
            || post.getAuthor().getFriends().contains(viewer);
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : UPLOADS_PATH + user.getAvatarFileName();
    }

    /** Gotowy adres do <iframe>. */
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

    /** Post moze skasowac jego autor, administrator aplikacji albo - przy poscie klanu - zarzad klanu. */
    private boolean canDelete(Post post, User viewer) {
        if (viewer == null) {
            return false;
        }
        if (post.getClan() != null) {
            ClanRole rola = post.getClan().roleOf(viewer.getId());
            if (rola != null && rola.manages()) {
                return true;
            }
        }
        return viewer.getRole() == Role.ADMIN
            || post.getAuthor().getUsername().equals(viewer.getUsername());
    }
}
