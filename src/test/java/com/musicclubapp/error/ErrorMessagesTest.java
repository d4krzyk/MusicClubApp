package com.musicclubapp.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Czy komunikaty bledow daja sie <b>faktycznie zlozyc</b> - w obu jezykach.
 *
 * <p><b>Skad wzial sie ten test.</b> Zakaz publikowania dzialal poprawnie:
 * post sie nie zapisywal, wyjatek leciał, test serwisu byl zielony. Tyle ze
 * uzytkownik zamiast komunikatu dostawal blad 500. Powod:
 * {@code MessageFormat} przy zapisie {@code {0,date,...}} nie potrafi
 * sformatowac {@code LocalDateTime} i rzucal wyjatkiem <b>w srodku obslugi
 * bledu</b> - czyli tam, gdzie nie ma juz komu go przechwycic.</p>
 *
 * <p>Test sprawdzajacy sam typ wyjatku nie mial szans tego zauwazyc, bo
 * wyjatek byl w porzadku. Zepsute bylo dopiero to, co z niego wynika.
 * Dlatego skladamy tu komunikat tak, jak robi to
 * {@link GlobalExceptionHandler}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Komunikaty bledow - czy w ogole daja sie zlozyc")
class ErrorMessagesTest {

    @Autowired
    private MessageSource messageSource;

    private String render(OperationNotAllowedException ex, Locale locale) {
        return messageSource.getMessage(ex.getMessageKey(), ex.getArguments(), locale);
    }

    /**
     * Termin kary <b>nie jest juz wklejany w komunikat</b> - i to jest zmiana,
     * a nie usterka.
     *
     * <p>Wczesniej ten test sprawdzal, ze komunikat zawiera „14.03.2026 09:47",
     * i przechodzil. Mimo to uzytkownik widzial bzdure: zakaz nalozony o 16:55
     * na godzine pokazywal sie jako <i>„do 15:55"</i>. Powod - serwer liczy
     * czas w UTC i formatowal te godzine u siebie, nie wiedzac nic o strefie
     * uzytkownika. Test byl zielony, bo pytal o <i>obecnosc</i> daty, a nie
     * o to, czy jest ona <i>prawdziwa</i> dla ogladajacego.</p>
     *
     * <p>Teraz komunikat mowi samo „zakaz publikowania", a chwila konca kary
     * jedzie osobnym polem i zamienia sie w godzine dopiero w przegladarce -
     * bo tylko ona zna zegar uzytkownika. Test pilnuje wiec dwoch rzeczy:
     * ze termin jest <b>przekazany</b> i ze <b>nie ma go w tekscie</b>.</p>
     */
    @Test
    @DisplayName("komunikat o zakazie NIE zawiera godziny - ta sklada przegladarka")
    void banMessageCarriesTheDeadlineSeparately() {
        LocalDateTime until = LocalDateTime.of(2026, 3, 14, 9, 47);
        var ex = OperationNotAllowedException.postingBanned(until);

        assertThat(ex.getDeadline())
            .describedAs("termin musi dojechac do przegladarki, inaczej nie ma z czego "
                + "zlozyc zdania o koncu kary")
            .isEqualTo(until);

        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            assertThat(render(ex, locale))
                .describedAs("godzina sformatowana przez SERWER byla by w jego strefie, "
                    + "a nie w strefie ogladajacego - w Polsce cofnieta o dwie godziny")
                .doesNotContain("09:47")
                .doesNotContain("14.03.2026");
        }
    }

    @Test
    @DisplayName("kazdy komunikat tej klasy ma tlumaczenie w OBU jezykach")
    void everyMessageExistsInBothLanguages() {
        /*
         * Brakujacy klucz konczy sie wyjatkiem przy skladaniu komunikatu -
         * a wiec bledem 500 zamiast czytelnej odpowiedzi. Wyliczenie
         * wszystkich przypadkow w jednym miejscu sprawia, ze nowy komunikat
         * bez tlumaczenia nie przejdzie niezauwazony.
         */
        var przypadki = new OperationNotAllowedException[] {
            OperationNotAllowedException.ownRole(),
            OperationNotAllowedException.ownAccount(),
            OperationNotAllowedException.someoneElsesPost(),
            OperationNotAllowedException.someoneElsesPostEdit(),
            OperationNotAllowedException.invitationToSelf(),
            OperationNotAllowedException.alreadyFriends(),
            OperationNotAllowedException.invitationAlreadySent(),
            OperationNotAllowedException.someoneElsesInvitation(),
            OperationNotAllowedException.favoritesLimit(),
            OperationNotAllowedException.notInCatalog(),
            OperationNotAllowedException.lastFmDisabled(),
            OperationNotAllowedException.lastFmUnknownUser(),
            OperationNotAllowedException.lastFmUnavailable(),
            OperationNotAllowedException.postingBanned(LocalDateTime.now().plusDays(1)),
        };

        for (var ex : przypadki) {
            for (var locale : new Locale[] { Locale.forLanguageTag("pl"), Locale.ENGLISH }) {
                assertThat(render(ex, locale))
                    .as("komunikat %s dla %s", ex.getMessageKey(), locale)
                    .isNotBlank()
                    // Nieprzetworzony klucz w tresci znaczy, ze tlumaczenia nie ma
                    .doesNotContain("{")
                    .doesNotContain(ex.getMessageKey());
            }
        }
    }
}
