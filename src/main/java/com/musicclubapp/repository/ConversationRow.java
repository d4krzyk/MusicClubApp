package com.musicclubapp.repository;

/**
 * Jedna rozmowa sprowadzona do dwoch liczb: z kim i ktora wiadomosc byla ostatnia.
 *
 * <p>Rekord wypelniany <b>wyrazeniem konstruktorowym</b> w JPQL
 * ({@code SELECT new ...ConversationRow(...)}) - tak samo jak {@link UnreadRow}.</p>
 */
public record ConversationRow(Long partnerId, Long lastMessageId) {
}
