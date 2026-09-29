package com.musicclubapp.entity;

/** Kto moze mnie zaprosic - do znajomych (a pozniej takze do klanu). */
public enum InvitePolicy {
    /** Kazdy. */
    EVERYONE,
    /** Tylko ktos, z kim mam wspolnego znajomego. */
    FRIENDS_OF_FRIENDS,
    /** Nikt - to ja zapraszam. */
    NOBODY
}
