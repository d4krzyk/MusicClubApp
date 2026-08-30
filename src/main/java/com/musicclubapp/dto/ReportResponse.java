package com.musicclubapp.dto;

import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Zgloszenie widziane przez administratora. */
public record ReportResponse(
    Long id,
    String reporterUsername,
    String reportedUsername,
    String reportedAvatarUrl,
    ReportReason reason,
    ReportContext context,
    /** Identyfikator posta albo {@code null}; post moze byc juz skasowany. */
    Long postId,
    String description,
    List<EvidenceLineResponse> evidence,
    ReportStatus status,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt,
    String resolvedBy,
    String resolutionNote,
    long priorResolved
) {
}
