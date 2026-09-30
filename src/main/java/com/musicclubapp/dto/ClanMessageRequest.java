package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClanMessageRequest(@NotBlank @Size(max = ClanMessage.MAX_CONTENT_LENGTH) String content) {
}
