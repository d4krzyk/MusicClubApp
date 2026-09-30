package com.musicclubapp.dto;

/** Skrot wiadomosci, na ktora odpowiada dana wiadomosc na czacie klanu. */
public record ClanReplyPreview(Long id, String senderUsername, String excerpt) {
}
