package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Kara nakladana na konto przez administratora.
 *
 * <p><b>Jeden rekord na obie kary</b> - zakaz publikowania i zakaz wysylania
 * wiadomosci. Wczesniej byly dwa ({@code PostingBanRequest} i
 * {@code MessagingBanRequest}), rozniace sie <i>wylacznie komentarzem</i>,
 * z uzasadnieniem, ze „nic nie gwarantuje, ze na zawsze beda mialy te same
 * ograniczenia". To bylo projektowanie pod roznice, ktora nie istnieje -
 * a kosztem byla ta sama koncepcja napisana dwa razy w dziesieciu miejscach
 * i realny blad, gdy poprawka trafila tylko do jednej sciezki.</p>
 *
 * <p><b>Rodzaju kary tu nie ma</b> i to jest celowe: mowi o nim adres
 * ({@code PATCH /api/users/{id}/bans/{kind}}), bo to on wskazuje, co
 * zmieniamy. Powtorzenie go w tresci zapytania pozwoliloby przyslac adres
 * i tresc, ktore mowia co innego - a wtedy trzeba by jeszcze rozstrzygac,
 * ktore z dwojga wygrywa.</p>
 *
 * <p><b>Dlaczego godziny, a nie data konca.</b> Administrator mysli
 * kategoriami "dzien przerwy", a nie "do 14 marca 09:47". Termin wylicza
 * serwer - i wylicza go od <b>swojego</b> zegara, a nie od czasu przyslanego
 * przez klienta. Data z zapytania dalaby sie ustawic na przeszlosc, czyli
 * zamienic kare w nic nieznaczacy wpis.</p>
 *
 * @param hours   ile godzin ma trwac kara; {@code null} <b>zdejmuje</b> kare
 * @param forever kara bezterminowa - wtedy {@code hours} nie ma znaczenia
 */
@Schema(description = "Kara: liczba godzin, kara bezterminowa "
    + "albo brak obu wartosci, zeby ja zdjac")
public record BanRequest(

    /*
     * Gorne ograniczenie to nie zlosliwosc wobec administratora, tylko
     * ochrona przed pomylka: 8760 godzin to rok. Kara "na 100 lat" wpisana
     * przez przypadek byla by skasowaniem konta bez powiedzenia tego wprost -
     * a kara naprawde bezterminowa ma wlasne, jawne pole ponizej.
     */
    @Min(value = 1, message = "{validation.ban.hours.min}")
    @Max(value = 8760, message = "{validation.ban.hours.max}")
    @Schema(description = "Ile godzin ma trwac kara (1-8760). Pusta wartosc ja zdejmuje.",
            example = "24")
    Integer hours,

    @Schema(description = "Kara bezterminowa. Gdy true, pole hours jest pomijane.",
            example = "false")
    Boolean forever
) {
}
