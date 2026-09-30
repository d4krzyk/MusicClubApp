package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanEmoji;

/** Ile osob zareagowalo tym emoji na wiadomosc i czy jest wsrod nich ogladajacy. */
public record ClanReactionCount(ClanEmoji type, long count, boolean mine) {
}
