package com.musicclubapp.mapper;

import com.musicclubapp.dto.NotificationResponse;
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

        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getActor().getUsername(),
            avatarUrl(notification.getActor()),
            notification.getReactionType(),
            post != null ? post.getId() : null,
            excerpt(post),
            link(notification),
            notification.isRead(),
            notification.getCreatedAt());
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }

    /** Dokad prowadzi klikniecie. */
    private String link(Notification notification) {
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
