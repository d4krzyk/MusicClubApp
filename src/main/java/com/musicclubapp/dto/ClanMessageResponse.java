package com.musicclubapp.dto;

import java.time.LocalDateTime;

public record ClanMessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    String content,
    LocalDateTime createdAt,
    boolean mine,
    boolean canDelete
) {
}
