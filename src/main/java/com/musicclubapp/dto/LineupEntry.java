package com.musicclubapp.dto;

import java.util.List;

/** Wykonawca w skladzie wydarzenia: gatunki (Last.fm), linki (z importu) i czy to moj ulubiony. */
public record LineupEntry(String name, List<String> tags, List<PerformerLinkView> links, boolean favorite) {
}
