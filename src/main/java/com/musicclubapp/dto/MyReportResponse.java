package com.musicclubapp.dto;

import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Wlasne zgloszenie widziane przez zglaszajacego. */
@Schema(description = "Wlasne zgloszenie razem z rozstrzygnieciem")
public record MyReportResponse(
    Long id,
    String reportedUsername,
    ReportReason reason,
    ReportContext context,
    LocalDateTime createdAt,
    ReportStatus status,
    String resolutionNote,
    LocalDateTime resolvedAt
) {
}
