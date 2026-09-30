package com.musicclubapp.dto;

/** Ile wiadomosci na czacie mojego klanu czeka nieprzeczytanych (0 i null, gdy nie ma klanu). */
public record ClanUnreadResponse(Long clanId, long unread) {
}
