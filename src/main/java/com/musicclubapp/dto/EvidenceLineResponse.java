package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Jedna linijka dowodu w zgloszeniu - migawka sprzed chwili zgloszenia. */
public record EvidenceLineResponse(
    String author,
    String text,
    LocalDateTime sentAt
) {
}
