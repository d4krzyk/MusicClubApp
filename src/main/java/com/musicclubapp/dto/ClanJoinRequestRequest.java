package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanJoinRequest;
import jakarta.validation.constraints.Size;

/** Prosba o dolaczenie do klanu, z opcjonalnym krotkim wstepem. */
public record ClanJoinRequestRequest(@Size(max = ClanJoinRequest.MESSAGE_MAX) String message) {
}
