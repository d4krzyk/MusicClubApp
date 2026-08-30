package com.musicclubapp.repository;

/** Jedna rozmowa sprowadzona do dwoch liczb: z kim i ktora wiadomosc byla ostatnia. */
public record ConversationRow(Long partnerId, Long lastMessageId) {
}
