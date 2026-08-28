package com.musicclubapp.dto;

import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nowe zgloszenie uzytkownika.
 *
 * <p><b>Kogo zglaszamy, jest w ADRESIE</b> ({@code POST /api/reports/{login}}),
 * a kto zglasza - w sesji. Zadnej z tych dwoch rzeczy nie da sie podmienic
 * trescia zapytania, wiec nie da sie ani zglosic w cudzym imieniu, ani
 * podstawic innego zglaszanego niz ten, ktorego profil sie oglada.</p>
 *
 * @param postId identyfikator posta - wymagany <b>tylko</b> przy
 *               {@link ReportContext#POST}; przy pozostalych ignorowany
 */
public record CreateReportRequest(

    @NotNull(message = "{validation.report.reason.required}")
    ReportReason reason,

    @NotNull(message = "{validation.report.context.required}")
    ReportContext context,

    Long postId,

    /**
     * Opis od zglaszajacego.
     *
     * <p><b>Obowiazkowy, i to jest decyzja przeciwko wygodzie zglaszajacego.</b>
     * Zgloszenie skladajace sie z samego wyboru z listy nie mowi
     * administratorowi nic ponad to, ze komus cos sie nie spodobalo. Wymog
     * napisania jednego zdania odsiewa przy okazji zgloszenia klikniete
     * ze zloscia, bez zastanowienia - a to jest tu efekt pozadany.</p>
     */
    @NotBlank(message = "{validation.report.description.required}")
    @Size(min = 10, max = Report.MAX_DESCRIPTION_LENGTH,
          message = "{validation.report.description.size}")
    String description
) {
}
