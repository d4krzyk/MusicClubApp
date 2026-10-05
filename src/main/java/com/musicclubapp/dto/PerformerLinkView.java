package com.musicclubapp.dto;

import com.musicclubapp.entity.PerformerLinkKind;

/** Link wykonawcy: rodzaj (przegladarka zna podpis) i adres. */
public record PerformerLinkView(PerformerLinkKind kind, String url) {
}
