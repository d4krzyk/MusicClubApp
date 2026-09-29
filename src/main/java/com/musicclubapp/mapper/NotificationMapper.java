package com.musicclubapp.mapper;

import com.musicclubapp.dto.NotificationResponse;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.Notification;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.User;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Encja powiadomienia -&gt; DTO, razem z adresem, pod ktory ma prowadzic. */
@Component
public class NotificationMapper {

    /** Ile znakow tresci posta pokazujemy w powiadomieniu. */
    private static final int EXCERPT_LENGTH = 60;

    public NotificationResponse toResponse(Notification notification) {
        Post post = notification.getPost();
        User actor = notification.getActor();
        MusicEvent event = notification.getEvent();

        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            actor != null ? actor.getUsername() : null,
            actor != null ? avatarUrl(actor) : null,
            notification.getReactionType(),
            post != null ? post.getId() : null,
            excerpt(post),
            link(notification),
            notification.isRead(),
            notification.getCreatedAt(),
            event != null ? event.getId() : null,
            event != null ? event.getName() : null,
            notification.getDaysLeft());
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }

    /** Dokad prowadzi klikniecie. */
    public String link(Notification notification) {
        return switch (notification.getType()) {
            case REACTION -> notification.getPost() != null
                ? "/post/" + notification.getPost().getId()
                : "/";
            case FRIEND_REQUEST -> "/znajomi";
            case FRIEND_ACCEPTED -> "/profil/"
                + URLEncoder.encode(notification.getActor().getUsername(), StandardCharsets.UTF_8);
            // Zgloszenie prowadzi do panelu, a nie na profil zglaszajacego -
            // administrator ma tam podjac decyzje, a nie ogladac czyjs profil
            case REPORT -> "/zgloszenia";
            // Zglaszajacy trafia na wlasna liste zgloszen, a nie do panelu admina
            case REPORT_RESOLVED -> "/moje-zgloszenia";
            case EVENT_REMINDER -> notification.getEvent() != null
                ? "/wydarzenia/" + notification.getEvent().getId()
                : "/wydarzenia?widok=moje";
        };
    }

    /** Poczatek tresci posta. */
    private String excerpt(Post post) {
        if (post == null || post.getContent() == null) {
            return null;
        }

        String content = post.getContent().strip();
        return content.length() <= EXCERPT_LENGTH
            ? content
            : content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }
}
