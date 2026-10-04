package com.musicclubapp.dto;

import com.musicclubapp.entity.ProfilePrompt;
import com.musicclubapp.entity.ProfilePromptAnswer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Jedno pytanie z odpowiedzia przy zapisie karty. */
public record PromptAnswerRequest(
    @NotNull(message = "{validation.card.prompt.notnull}") ProfilePrompt prompt,
    @NotBlank(message = "{validation.card.answer.notblank}")
    @Size(max = ProfilePromptAnswer.MAX_ANSWER, message = "{validation.card.answer.size}") String answer
) {
}
