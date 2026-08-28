package com.musicclubapp.mapper;

import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicEmbed;
import org.springframework.stereotype.Component;

/**
 * Przepisuje encje {@link Message} na DTO wysylane do przegladarki.
 *
 * <p>Ta sama rola co {@link PostMapper} i te same zasady - w szczegolnosci
 * adresy odtwarzacza sklada {@link MusicEmbed}, wiec frontend nie musi znac
 * formatu zadnego serwisu.</p>
 */
@Component
public class MessageMapper {

    /**
     * @param viewer kto oglada - po nim rozstrzyga sie pole {@code mine}.
     *               <b>Nie moze byc {@code null}</b>: wiadomosci nie widzi
     *               nikt niezalogowany, wiec brak ogladajacego oznaczalby
     *               blad w kodzie, a nie sytuacje do obsluzenia
     */
    public MessageResponse toResponse(Message message, User viewer) {
        User sender = message.getSender();

        return new MessageResponse(
            message.getId(),
            sender.getUsername(),
            avatarUrl(sender),
            message.getContent(),
            playerUrl(message),
            message.getMusicProvider(),
            message.getMusicKind(),
            message.getMusicTitle(),
            message.getMusicThumbnailUrl(),
            message.getMusicStartSeconds(),
            pageUrl(message),
            message.getCreatedAt(),
            sender.getId().equals(viewer.getId()),
            message.isRead());
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }

    /** Gotowy adres do {@code <iframe>} albo {@code null}, gdy nie ma nagrania. */
    private String playerUrl(Message message) {
        if (!message.hasMusic()) {
            return null;
        }
        return MusicEmbed.embedUrl(
            message.getMusicProvider(),
            message.getMusicKind(),
            message.getMusicExternalId(),
            message.getMusicStartSeconds());
    }

    /** Adres strony w serwisie - pod przycisk "otworz w serwisie". */
    private String pageUrl(Message message) {
        if (!message.hasMusic()) {
            return null;
        }
        return MusicEmbed.canonicalUrl(
            message.getMusicProvider(), message.getMusicKind(), message.getMusicExternalId());
    }
}
