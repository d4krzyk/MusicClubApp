package com.musicclubapp.dto;

import java.time.LocalDate;

/**
 * Wydarzenie, pod ktorym napisano post - tyle, ile trzeba na plakietke
 * z odnosnikiem na tablicy. Miasto jako klucz ("krakow") i nazwa z Ticketmastera:
 * frontend tlumaczy klucz, a nazwa jest na wypadek miasta spoza listy.
 */
public record PostEventRef(Long id, String name, LocalDate date, String cityKey, String city) {
}
