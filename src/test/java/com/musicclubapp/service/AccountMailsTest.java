package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kazdy rodzaj wiadomosci w obu jezykach: bez dziur w szablonie i bez
 * podwojnych apostrofow. Apostrof w pliku messages zachowuje sie roznie:
 * w tekscie z argumentami trzeba go podwoic ("we''ve"), a w tekscie bez
 * argumentow podwojony zostaje podwojony - latwo sie pomylic w jedna strone.
 */
@DisplayName("Wiadomosci o koncie - kazdy rodzaj, PL i EN")
class AccountMailsTest {

    private static AccountMails mails() {
        ReloadableResourceBundleMessageSource messages = new ReloadableResourceBundleMessageSource();
        messages.setBasename("classpath:lang/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new AccountMails(messages);
    }

    @ParameterizedTest
    @EnumSource(AccountMails.Kind.class)
    void everyKindRendersCleanly(AccountMails.Kind kind) {
        for (Locale jezyk : new Locale[] {Locale.forLanguageTag("pl"), Locale.ENGLISH}) {
            MailService.Mail m = mails().compose(kind, "ola@example.com", "ola_<b>", "https://x.pl/a?token=abc",
                "n***y@example.com", jezyk);

            assertThat(m.subject()).isNotBlank();
            assertThat(m.html())
                .contains("https://x.pl/a?token=abc", "cid:napis", "ola_&lt;b&gt;")
                .doesNotContain("{{", "}}", "''", "{0}", "{1}", "ola_<b>");
            assertThat(m.text()).contains("https://x.pl/a?token=abc").doesNotContain("''", "{0}", "{1}");
            if (kind == AccountMails.Kind.CHANGE_OLD) {
                assertThat(m.html()).contains("n***y@example.com");
            }
        }
    }
}
