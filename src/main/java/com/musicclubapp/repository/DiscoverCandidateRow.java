package com.musicclubapp.repository;

import java.time.LocalDateTime;

/** Jedna osoba z talii trybu Poznawaj - z gustem wspolnym z ogladajacym i odlegloscia miast. */
public interface DiscoverCandidateRow {

    Long getUserId();

    String getUsername();

    String getAvatarFileName();

    String getBio();

    /** "Szukam" w postaci z bazy (nazwy po przecinku) - patrz LookingForConverter. */
    String getLookingFor();

    String getCity();

    /** Czy osoba pokazuje miasto - bez tego karta nie ma podpisu ani pasma odleglosci. */
    boolean getCityVisible();

    /** Profil dla wszystkich - tylko wtedy karta pokazuje pozostalych ulubionych (nie tylko wspolnych). */
    boolean getProfileOpen();

    long getSharedArtists();

    long getSharedTracks();

    long getSharedGenres();

    long getSharedFriends();

    /** Odleglosc miast w km; 0 = to samo miasto, null = nie wiadomo. */
    Double getDistanceKm();

    LocalDateTime getCreatedAt();
}
