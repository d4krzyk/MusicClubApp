package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanEmoji;

/** Ile osob zareagowalo danym emoji na wiadomosc czatu klanu i czy jest wsrod nich ogladajacy. */
public interface ClanReactionRow {

    Long getMessageId();

    ClanEmoji getType();

    long getTotal();

    Long getMine();
}
