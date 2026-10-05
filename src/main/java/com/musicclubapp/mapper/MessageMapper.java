package com.musicclubapp.mapper;

import com.musicclubapp.dto.MeetingResponse;
import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicEmbed;
import org.springframework.stereotype.Component;

/** Przepisuje encje Message na DTO wysylane do przegladarki. */
@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message, User viewer) {
        return toResponse(message, viewer, null);
    }

    /** {@code meeting} - spotkanie z wiadomosci juz w postaci dla ogladajacego (liczy je {@code MeetingService}). */
    public MessageResponse toResponse(Message message, User viewer, MeetingResponse meeting) {
        User sender = message.getSender();

        return new MessageResponse(
            message.getId(),
            sender.getUsername(),
            avatarUrl(sender),
            message.getContent(),
            message.getGif() == null ? null : message.getGif().toView(),
            playerUrl(message),
            message.getMusicProvider(),
            message.getMusicKind(),
            message.getMusicTitle(),
            message.getMusicThumbnailUrl(),
            message.getMusicStartSeconds(),
            pageUrl(message),
            message.getCreatedAt(),
            sender.getId().equals(viewer.getId()),
            message.isRead(),
            message.isDeleted(),
            meeting);
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
