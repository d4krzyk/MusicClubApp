package com.musicclubapp.dto;

import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * @param action   co zrobic z kontem albo postem; {@code null} znaczy
 *                 {@link ModerationAction#NONE}
 * @param hours    czas trwania zakazu, gdy {@code action} jest kara czasowa
 * @param forever  zakaz bezterminowy - wtedy {@code hours} nie ma znaczenia
 */
public record ResolveReportRequest(

    @NotNull(message = "{validation.report.decision.required}")
    ReportStatus decision,

    @NotBlank(message = "{validation.report.note.required}")
    @Size(max = Report.MAX_DESCRIPTION_LENGTH, message = "{validation.report.note.size}")
    String note,

    /*
     * Bez @NotNull celowo. Brak wartosci znaczy "nic nie robimy" - czyli
     * dokladnie to, co robilo zamkniecie zgloszenia, zanim dzialania w ogole
     * istnialy. Dzieki temu starsze zapytania (i testy) dzialaja bez zmian,
     * a domyslna sciezka jest ta najlagodniejsza, a nie najsurowsza.
     */
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
