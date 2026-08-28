package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Jedna linijka dowodu w zgloszeniu - migawka sprzed chwili zgloszenia.
 *
 * <p>Odpowiednik encji osadzonej {@code ReportEvidence}. Osobny typ, bo
 * encje nie wychodza na zewnatrz - ta sama zasada co wszedzie indziej
 * w tym projekcie.</p>
 */
public record EvidenceLineResponse(
    String author,
    String text,
    LocalDateTime sentAt
) {
}
