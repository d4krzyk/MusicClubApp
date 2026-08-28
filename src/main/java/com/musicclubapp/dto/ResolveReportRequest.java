package com.musicclubapp.dto;

import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decyzja administratora w sprawie zgloszenia.
 *
 * @param decision {@link ReportStatus#RESOLVED} (zasadne, podjeto dzialanie)
 *                 albo {@link ReportStatus#DISMISSED} (bezpodstawne).
 *                 Wartosc {@code OPEN} nie ma tu sensu i jest odrzucana
 *                 w serwisie - "zamykam jako otwarte" nie jest decyzja
 * @param note     co zostalo zrobione albo dlaczego nie; obowiazkowe -
 *                 patrz komentarz przy {@code Report.resolutionNote}
 */
public record ResolveReportRequest(

    @NotNull(message = "{validation.report.decision.required}")
    ReportStatus decision,

    @NotBlank(message = "{validation.report.note.required}")
    @Size(max = Report.MAX_DESCRIPTION_LENGTH, message = "{validation.report.note.size}")
    String note
) {
}
