package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanTitleMode;

/** Tytul przy czlonku klanu. */
public record ClanTitleBadge(Long id, String name, String color, String colorHex, ClanTitleMode mode) {
}
