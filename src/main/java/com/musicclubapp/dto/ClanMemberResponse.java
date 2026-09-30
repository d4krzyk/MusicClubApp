package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanRole;

import java.time.LocalDateTime;

public record ClanMemberResponse(
    String username,
    String avatarUrl,
    ClanRole role,
    LocalDateTime joinedAt,
    boolean me,
    /** Na jaki kolor glosuje ta osoba - widoczne dla wszystkich w klanie, glosowanie jest jawne. */
    ClanColor vote
) {
}
