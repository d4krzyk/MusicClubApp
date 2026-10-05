package com.musicclubapp.mapper;

import com.musicclubapp.dto.NotificationResponse;
import com.musicclubapp.entity.Meeting;
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
        Meeting meeting = notification.getMeeting();

        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            actor != null ? actor.getUsername() : null,
            actor != null ? avatarUrl(actor) : null,
            notification.getReactionType(),
            post != null ? post.getId() : null,
            excerpt(notification.getComment() != null ? notification.getComment().getContent()
                : post != null ? post.getContent() : null),
            link(notification),
            notification.isRead(),
            notification.getCreatedAt(),
            event != null ? event.getId() : null,
            event != null ? event.getName() : null,
            notification.getDaysLeft(),
            notification.getClan() != null ? notification.getClan().getId() : null,
            notification.getClan() != null ? notification.getClan().getName() : null,
            meeting != null ? meeting.getId() : null,
            meeting != null ? meeting.getPlace() : null,
            meeting != null ? meeting.getStartsAt() : null);
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
            // Do komentarza: strona posta otwiera jego komentarze i przewija do tego jednego
            case POST_COMMENT, COMMENT_REPLY, COMMENT_MENTION -> notification.getPost() != null
                ? "/post/" + notification.getPost().getId()
                    + (notification.getComment() != null ? "?komentarz=" + notification.getComment().getId() : "")
                : "/";
            case FRIEND_REQUEST -> "/znajomi";
            case FRIEND_ACCEPTED, DISCOVER_MATCH -> "/profil/"
                + URLEncoder.encode(notification.getActor().getUsername(), StandardCharsets.UTF_8);
            // Zgloszenie prowadzi do panelu, a nie na profil zglaszajacego -
            // administrator ma tam podjac decyzje, a nie ogladac czyjs profil
            case REPORT -> "/zgloszenia";
            // Zglaszajacy trafia na wlasna liste zgloszen, a nie do panelu admina
            case REPORT_RESOLVED -> "/moje-zgloszenia";
            case CLAN_INVITE, CLAN_KICKED, CLAN_JOIN_REQUEST, CLAN_REQUEST_ACCEPTED -> "/klan";
            // Spotkanie z rozmowy otwiera rozmowe z druga strona (sprawca), spotkanie klanu - czat klanu
            case MEETING_REMINDER, MEETING_CANCELLED -> notification.getClan() == null && notification.getActor() != null
                ? "/?czat=" + URLEncoder.encode(notification.getActor().getUsername(), StandardCharsets.UTF_8)
                : "/klan";
            case EVENT_REMINDER -> notification.getEvent() != null
                ? "/wydarzenia/" + notification.getEvent().getId()
                : "/wydarzenia?widok=moje";
        };
    }

    /** Poczatek tresci posta albo komentarza. */
    private String excerpt(String text) {
        if (text == null) {
            return null;
        }

        String content = text.strip();
        return content.length() <= EXCERPT_LENGTH
            ? content
            : content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }
}
