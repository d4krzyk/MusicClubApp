package com.musicclubapp.dto;

/**
 * Skrot wiadomosci, na ktora odpowiada dana wiadomosc na czacie klanu. {@code gif} - tamta wiadomosc niesie
 * GIF-a (przy samym GIF-ie skrot jest pusty, a przegladarka pisze "GIF"); {@code deleted} - tamta wiadomosc zostala
 * usunieta (skrot pusty, przegladarka pisze "wiadomosc usunieta").
 */
public record ClanReplyPreview(Long id, String senderUsername, String excerpt, boolean gif, boolean deleted) {
}
