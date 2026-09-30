package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Jaka wersje regulaminu ma zaakceptowana konto i czy to jest obecna. */
public record TermsStatusResponse(String version, String acceptedVersion, LocalDateTime acceptedAt, boolean current) {
}
