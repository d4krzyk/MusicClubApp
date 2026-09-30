package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanRole;
import jakarta.validation.constraints.NotNull;

/** Nowa rola czlonka: ADMIN albo MEMBER (zalozyciela zmienia sie tylko przekazaniem klanu). */
public record ClanRoleRequest(@NotNull ClanRole role) {
}
