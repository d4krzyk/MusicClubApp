package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Prosba czekajaca na zakladajacego. */
public record CrewRequestView(Long id, String username, String avatarUrl, String message, LocalDateTime createdAt) {
}
