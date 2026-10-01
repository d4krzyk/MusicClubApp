package com.musicclubapp.repository;

/** Liczba komentarzy pod jednym postem albo odpowiedzi pod jednym komentarzem. */
public interface CommentCountRow {

    Long getOwnerId();

    long getTotal();
}
