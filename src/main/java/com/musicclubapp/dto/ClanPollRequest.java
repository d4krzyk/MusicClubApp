package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanPoll;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Nowa ankieta; {@code days} - na ile dni (1, 3, 7 albo 14; domyslnie 7). */
public record ClanPollRequest(
    @NotBlank @Size(max = ClanPoll.QUESTION_MAX) String question,
    @NotNull @Size(min = ClanPoll.OPTIONS_MIN, max = ClanPoll.OPTIONS_MAX)
    List<@NotBlank @Size(max = ClanPoll.OPTION_MAX) String> options,
    Integer days
) {
}
