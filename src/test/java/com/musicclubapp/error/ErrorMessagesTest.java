package com.musicclubapp.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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

    @Test
    @DisplayName("komunikat o zakazie podaje TERMIN - po polsku i po angielsku")
    void banMessageCarriesTheDeadline() {
        var ex = OperationNotAllowedException.postingBanned(
            LocalDateTime.of(2026, 3, 14, 9, 47));

        assertThat(render(ex, Locale.forLanguageTag("pl")))
            .contains("14.03.2026")
            .contains("09:47");

        assertThat(render(ex, Locale.ENGLISH))
            .contains("14.03.2026")
            .contains("09:47");
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
