package com.musicclubapp.dto;

import com.musicclubapp.entity.ProfilePrompt;

/** Odpowiedz na pytanie muzyczne z karty profilu. */
public record PromptAnswerView(ProfilePrompt prompt, String answer) {
}
