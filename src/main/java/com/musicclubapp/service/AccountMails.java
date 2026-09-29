package com.musicclubapp.service;

import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wiadomosci o koncie - w wersji HTML i zwyklym tekstem: potwierdzenie
 * adresu, zgoda na zmiane adresu, reset hasla i powiadomienie o zmianie hasla.
 *
 * <p>Teksty sa w plikach lang/messages*, jak reszta aplikacji; tu jest tylko
 * wstawianie ich w szablon mail/potwierdzenie.html. Wersja tekstowa idzie
 * razem z HTML-em: czesc programow (i filtrow antyspamowych) patrzy wlasnie
 * na nia, a wiadomosc bez niej czesciej laduje w spamie.</p>
 */
@Component
public class AccountMails {

    /**
     * Rodzaj wiadomosci. Prefiks to poczatek kluczy w messages*.properties
     * (mail.&lt;prefiks&gt;.subject itd.), a waznosc - klucz zdania o tym, jak
     * dlugo dziala link (pusty = bez tego zdania).
     */
    public enum Kind {
        /** Nowe konto - potwierdz adres. */
        REGISTRATION("register", "mail.validity"),
        /** Nowy adres przy zmianie - potwierdz, ze to twoja skrzynka. */
        CHANGE_NEW("change", "mail.validity"),
        /** Stary adres przy zmianie - zgoda albo "to nie ja". */
        CHANGE_OLD("approve", "mail.validity"),
        /** Nie pamietam hasla. */
        PASSWORD_RESET("reset", "mail.validity.reset"),
        /** Haslo zostalo zmienione - na wypadek, gdyby to nie byl wlasciciel. */
        PASSWORD_CHANGED("pwchanged", "");

        final String prefiks;
        final String waznosc;

        Kind(String prefiks, String waznosc) {
            this.prefiks = prefiks;
            this.waznosc = waznosc;
        }
    }

    /** Napis MusicClub na bialo - lezy na gradiencie w naglowku. */
    static final MailService.Inline NAPIS = new MailService.Inline("napis", "mail/napis-musicclub.png");

    /** Wysokosc napisu przy szerokosci 210 px w wiadomosci - z proporcji pliku PNG (630 x 119). */
    private static final int NAPIS_WYSOKOSC = 40;

    private static final Pattern POLE = Pattern.compile("\\{\\{(\\w+)}}");

    /** Fragment szablonu, ktory znika, gdy jego pole jest puste: {{#pole}}...{{/pole}}. */
    private static final Pattern SEKCJA = Pattern.compile("(?s)\\{\\{#(\\w+)}}(.*?)\\{\\{/\\1}}");

    private final MessageSource messages;
    private final String szablon;

    public AccountMails(MessageSource messages) {
        this.messages = messages;
        // Komentarze w szablonie sa dla nas, nie dla odbiorcy - nie wysylamy ich
        this.szablon = wczytaj("mail/potwierdzenie.html").replaceAll("(?s)<!--.*?-->\\s*", "");
    }

    /**
     * @param dodatek drugi argument zdania wstepnego - np. zamaskowany nowy
     *                adres w prosbie o zgode na zmiane; moze byc pusty
     */
    public MailService.Mail compose(Kind kind, String to, String username, String link, String dodatek,
                                    Locale locale) {
        String rodzaj = kind.prefiks;
        String temat = tekst("mail." + rodzaj + ".subject", locale);

        Map<String, String> pola = new LinkedHashMap<>();
        pola.put("lang", locale.getLanguage());
        pola.put("subject", temat);
        pola.put("preheader", tekst("mail." + rodzaj + ".preheader", locale));
        pola.put("heading", tekst("mail." + rodzaj + ".heading", locale));
        pola.put("greeting", tekst("mail.greeting", locale, username));
        pola.put("intro", tekst("mail." + rodzaj + ".intro", locale, username, dodatek == null ? "" : dodatek));
        pola.put("button", tekst("mail." + rodzaj + ".button", locale));
        pola.put("link", link);
        pola.put("fallback", tekst("mail.fallback", locale));
        pola.put("validity", kind.waznosc.isEmpty() ? "" : tekst(kind.waznosc, locale));
        pola.put("ignore", tekst("mail." + rodzaj + ".ignore", locale));
        pola.put("tagline", tekst("mail.tagline", locale));
        pola.put("auto", tekst("mail.auto", locale));
        pola.put("napisWysokosc", String.valueOf(NAPIS_WYSOKOSC));

        String html = wypelnij(szablon, pola);
        String zwykly = String.join("\n",
            pola.get("heading"),
            "",
            pola.get("greeting"),
            pola.get("intro"),
            "",
            pola.get("button") + ":",
            link,
            "",
            pola.get("validity").isEmpty() ? pola.get("ignore") : pola.get("validity") + "\n" + pola.get("ignore"),
            "",
            "-- ",
            "MusicClub - " + pola.get("tagline"),
            pola.get("auto"));

        return new MailService.Mail(to, temat, zwykly, html, Map.of(NAPIS.contentId(), NAPIS));
    }

    /** Kazda wartosc zamieniona na bezpieczny HTML - login czy adres moga zawierac "<". */
    private static String wypelnij(String szablon, Map<String, String> pola) {
        Matcher sekcje = SEKCJA.matcher(szablon);
        StringBuilder bezPustych = new StringBuilder();
        while (sekcje.find()) {
            String wartosc = pola.get(sekcje.group(1));
            sekcje.appendReplacement(bezPustych,
                Matcher.quoteReplacement(wartosc == null || wartosc.isEmpty() ? "" : sekcje.group(2)));
        }
        sekcje.appendTail(bezPustych);

        Matcher m = POLE.matcher(bezPustych);
        StringBuilder wynik = new StringBuilder();
        while (m.find()) {
            String wartosc = pola.get(m.group(1));
            if (wartosc == null) {
                throw new IllegalStateException("Szablon wiadomosci: brak wartosci dla {{" + m.group(1) + "}}");
            }
            m.appendReplacement(wynik, Matcher.quoteReplacement(HtmlUtils.htmlEscape(wartosc, "UTF-8")));
        }
        m.appendTail(wynik);
        return wynik.toString();
    }

    private String tekst(String klucz, Locale locale, Object... argumenty) {
        return messages.getMessage(klucz, argumenty, locale);
    }

    private static String wczytaj(String zasob) {
        try (InputStream in = new ClassPathResource(zasob).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Brak szablonu wiadomosci: " + zasob, e);
        }
    }
}
