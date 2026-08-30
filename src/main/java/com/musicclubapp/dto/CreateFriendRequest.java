package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;

/** Kogo chce zaprosic do znajomych. */
public record CreateFriendRequest(

    @NotBlank(message = "{validation.friend.username.required}")
    String username
) {
}
