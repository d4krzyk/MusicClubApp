package com.musicclubapp.dto;

import com.musicclubapp.entity.InvitationStatus;

import java.time.LocalDateTime;

/** Moja prosba o dolaczenie do klanu - na stronie "Klan", gdy nie jestem w zadnym. */
public record ClanMyRequest(Long id, ClanBadge clan, InvitationStatus status, LocalDateTime createdAt) {
}
