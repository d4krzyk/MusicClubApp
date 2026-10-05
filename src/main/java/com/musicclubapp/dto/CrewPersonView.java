package com.musicclubapp.dto;

import com.musicclubapp.entity.CrewRole;

/** Osoba w ekipie: login, awatar, rola. */
public record CrewPersonView(String username, String avatarUrl, CrewRole role) {
}
