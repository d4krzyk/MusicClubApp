package com.musicclubapp.entity;

/**
 * Reakcje na wiadomosci czatu klanu. Stala lista, a nie dowolne emoji: zestaw jest krotki
 * i taki sam na kazdym telefonie, a w bazie zostaje nazwa, nie znak (kolumna z ograniczeniem
 * CHECK jak przy pozostalych wyliczeniach).
 */
public enum ClanEmoji {
    THUMBS_UP,
    HEART,
    LAUGH,
    FIRE,
    MUSIC,
    WOW
}
