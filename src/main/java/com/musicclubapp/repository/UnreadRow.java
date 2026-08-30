package com.musicclubapp.repository;

/** Ile nieprzeczytanych wiadomosci przyszlo od jednej osoby. */
public record UnreadRow(Long senderId, long count) {
}
