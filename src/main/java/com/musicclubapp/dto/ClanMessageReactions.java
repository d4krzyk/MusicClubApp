package com.musicclubapp.dto;

import java.util.List;

/** Reakcje pod jedna wiadomoscia czatu klanu (odswiezanie reakcji przy odpytywaniu czatu). */
public record ClanMessageReactions(Long messageId, List<ClanReactionCount> reactions) {
}
