package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Zakaz wysylania wiadomosci nakladany przez administratora.
 *
 * <p><b>Osobny rekord od {@link PostingBanRequest}, choc pole jest to samo.</b>
 * Nie dlatego, ze tak wygodniej - dlatego, ze to sa dwie rozne kary i nic nie
 * gwarantuje, ze na zawsze beda mialy te same ograniczenia. Wspolny typ
 * o nazwie "BanRequest" wygladalby dzis oszczedniej, a przy pierwszej roznicy
 * (choćby innym gornym limicie) trzeba by go i tak rozdzielic - tyle ze wtedy
 * z dzialajacym kodem po obu stronach.</p>
 *
 * @param hours   ile godzin ma trwac zakaz; {@code null} <b>zdejmuje</b> zakaz
 * @param forever zakaz bezterminowy - wtedy {@code hours} nie ma znaczenia
 */
@Schema(description = "Zakaz wysylania wiadomosci: liczba godzin, zakaz bezterminowy "
    + "albo brak obu wartosci, zeby go zdjac")
public record MessagingBanRequest(

    @Min(value = 1, message = "{validation.ban.hours.min}")
    @Max(value = 8760, message = "{validation.ban.hours.max}")
    @Schema(description = "Ile godzin ma trwac zakaz (1-8760). Pusta wartosc zdejmuje zakaz.",
            example = "48")
    Integer hours,

    /* Patrz komentarz przy tym samym polu w PostingBanRequest. */
    @Schema(description = "Zakaz bezterminowy. Gdy true, pole hours jest pomijane.",
            example = "false")
    Boolean forever
) {
}
