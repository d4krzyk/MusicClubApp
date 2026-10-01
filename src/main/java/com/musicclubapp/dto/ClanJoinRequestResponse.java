package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Prosba o dolaczenie widziana przez zarzad klanu. */
public record ClanJoinRequestResponse(Long id, String username, String avatarUrl, String message,
                                      LocalDateTime createdAt) {
}
