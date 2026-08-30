package com.musicclubapp.dto;

import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decyzja administratora w sprawie zgloszenia. */
public record ResolveReportRequest(

    @NotNull(message = "{validation.report.decision.required}")
    ReportStatus decision,

    @NotBlank(message = "{validation.report.note.required}")
    @Size(max = Report.MAX_DESCRIPTION_LENGTH, message = "{validation.report.note.size}")
    String note,

    /* Bez @NotNull celowo. */
    ModerationAction action,

    @Min(value = 1, message = "{validation.ban.hours.min}")
    @Max(value = 8760, message = "{validation.ban.hours.max}")
    Integer hours,

    Boolean forever
) {

    /** Wybrane dzialanie, z {@link ModerationAction#NONE} jako domyslnym. */
    public ModerationAction actionOrNone() {
        return action == null ? ModerationAction.NONE : action;
    }
}
