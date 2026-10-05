package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Wiadomosc na czacie ekipy. */
public record CrewMessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    String content,
    LocalDateTime createdAt,
    boolean mine,
    boolean canDelete,
    boolean deleted,
    MeetingResponse meeting
) {
}
