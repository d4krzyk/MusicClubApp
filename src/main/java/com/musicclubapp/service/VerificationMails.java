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
 * Tresc wiadomosci z linkiem potwierdzajacym - w wersji HTML i zwyklym tekstem.
 *
 * <p>Teksty sa w plikach lang/messages*, jak reszta aplikacji; tu jest tylko
 * wstawianie ich w szablon mail/potwierdzenie.html. Wersja tekstowa idzie
 * razem z HTML-em: czesc programow (i filtrow antyspamowych) patrzy wlasnie
 * na nia, a wiadomosc bez niej czesciej laduje w spamie.</p>
 */
@Component
public class VerificationMails {

    /** Po co ten link: nowe konto czy nowy adres w istniejacym koncie. */
    public enum Kind { REGISTRATION, CHANGE }

    /** Napis MusicClub na bialo - lezy na gradiencie w naglowku. */
    static final MailService.Inline NAPIS = new MailService.Inline("napis", "mail/napis-musicclub.png");

    /** Wysokosc napisu przy szerokosci 210 px w wiadomosci - z proporcji pliku PNG (630 x 119). */
    private static final int NAPIS_WYSOKOSC = 40;

    private static final Pattern POLE = Pattern.compile("\\{\\{(\\w+)}}");

    private final MessageSource messages;
    private final String szablon;

    public VerificationMails(MessageSource messages) {
        this.messages = messages;
        // Komentarze w szablonie sa dla nas, nie dla odbiorcy - nie wysylamy ich
        this.szablon = wczytaj("mail/potwierdzenie.html").replaceAll("(?s)<!--.*?-->\\s*", "");
    }

    public MailService.Mail compose(Kind kind, String to, String username, String link, Locale locale) {
        String rodzaj = kind == Kind.REGISTRATION ? "register" : "change";
        String temat = tekst("mail." + rodzaj + ".subject", locale);

        Map<String, String> pola = new LinkedHashMap<>();
        pola.put("lang", locale.getLanguage());
        pola.put("subject", temat);
        pola.put("preheader", tekst("mail." + rodzaj + ".preheader", locale));
        pola.put("heading", tekst("mail." + rodzaj + ".heading", locale));
        pola.put("greeting", tekst("mail.greeting", locale, username));
        pola.put("intro", tekst("mail." + rodzaj + ".intro", locale, username));
        pola.put("button", tekst("mail." + rodzaj + ".button", locale));
        pola.put("link", link);
        pola.put("fallback", tekst("mail.fallback", locale));
        pola.put("validity", tekst("mail.validity", locale));
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
            pola.get("validity"),
            pola.get("ignore"),
            "",
            "-- ",
            "MusicClub - " + pola.get("tagline"),
            pola.get("auto"));

        return new MailService.Mail(to, temat, zwykly, html, Map.of(NAPIS.contentId(), NAPIS));
    }

    /** Kazda wartosc zamieniona na bezpieczny HTML - login czy adres moga zawierac "<". */
    private static String wypelnij(String szablon, Map<String, String> pola) {
        Matcher m = POLE.matcher(szablon);
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
