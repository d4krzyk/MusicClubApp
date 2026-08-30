package com.musicclubapp.dto;

import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nowe zgloszenie uzytkownika. */
public record CreateReportRequest(

    @NotNull(message = "{validation.report.reason.required}")
    ReportReason reason,

    @NotNull(message = "{validation.report.context.required}")
    ReportContext context,

    Long postId,

    /** Opis od zglaszajacego. */
    @NotBlank(message = "{validation.report.description.required}")
    @Size(min = 10, max = Report.MAX_DESCRIPTION_LENGTH,
          message = "{validation.report.description.size}")
    String description
) {
}
