package com.musicclubapp.error;

import com.musicclubapp.entity.BanKind;

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

/** Czy komunikaty bledow daja sie faktycznie zlozyc - w obu jezykach. */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Komunikaty bledow - czy w ogole daja sie zlozyc")
class ErrorMessagesTest {

    @Autowired
    private MessageSource messageSource;

    private String render(OperationNotAllowedException ex, Locale locale) {
        return messageSource.getMessage(ex.getMessageKey(), ex.getArguments(), locale);
    }

    /** Termin kary nie jest juz wklejany w komunikat - i to jest zmiana, a nie usterka. */
    @Test
    @DisplayName("komunikat o zakazie NIE zawiera godziny - ta sklada przegladarka")
    void banMessageCarriesTheDeadlineSeparately() {
        LocalDateTime until = LocalDateTime.of(2026, 3, 14, 9, 47);
        var ex = OperationNotAllowedException.banned(BanKind.POSTING, until);

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
         * Brakujacy klucz konczy sie wyjatkiem przy skladaniu komunikatu - a wiec bledem 500
         * zamiast czytelnej odpowiedzi.
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
            OperationNotAllowedException.banned(BanKind.POSTING, LocalDateTime.now().plusDays(1)),
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
