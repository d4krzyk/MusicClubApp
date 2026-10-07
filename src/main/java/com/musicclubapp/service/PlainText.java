package com.musicclubapp.service;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tekst z obcego serwisu (opisy z Ticketmastera) jako zwykly tekst do pokazania - przegladarka i tak wstawia go jako
 * tekst, wiec znaczniki bylyby widac doslownie.
 *
 * Akapity i nowe linie zostaja (strona pokazuje je przez {@code white-space: pre-line}), znaczniki w zdaniu ({@code <b>},
 * {@code <a>}) znikaja bez sladu - jak w {@link LastFmService#bioText}, gdzie zamiana na spacje dawala "Warszawie .".
 * Encje sa rozkodowane po zdjeciu znacznikow, wiec {@code &lt;b&gt;} zostaje napisem. Zwykly tekst z "&lt;" (np. "&lt;3")
 * nie jest HTML-em i zostaje.
 */
final class PlainText {

    /** Cos, co wyglada na znacznik: "<p>", "</b>", "<br/>" - a nie "<3" ani "a < b". */
    private static final Pattern ZNACZNIK = Pattern.compile("</?[A-Za-z][^>]*>");

    private PlainText() {
    }

    /** Zwykly tekst albo null, gdy nic nie zostalo. */
    static String of(String tekst) {
        if (tekst == null || tekst.isBlank()) {
            return null;
        }
        String t = tekst.replace("\r\n", "\n").replace('\r', '\n');
        if (ZNACZNIK.matcher(t).find() || t.contains("<!--")) {
            t = t.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|div|h[1-6]|ul|ol|blockquote|table|tr|section|article)\\b[^>]*>", "\n\n")
                .replaceAll("(?i)<li\\b[^>]*>", "\n")
                .replaceAll("</?[A-Za-z][^>]*>", "");
        }
        t = HtmlUtils.htmlUnescape(t).replace(' ', ' ');

        StringBuilder wynik = new StringBuilder();
        for (String linia : t.split("\n", -1)) {
            wynik.append(linia.replaceAll("[ \\t\\f\\x0B]+", " ").strip()).append('\n');
        }
        String gotowy = wynik.toString().replaceAll("\n{3,}", "\n\n").strip();
        return gotowy.isEmpty() ? null : gotowy;
    }

    /**
     * Kilka tekstow o tym samym w jednym: bez pustych i bez powtorzen - tekst zawarty w innym (bez wielkosci liter
     * i odstepow) odpada, z dwoch takich samych zostaje pierwszy. Kolejnosc zostaje; akapity oddziela pusta linia.
     */
    static String joined(String... teksty) {
        List<String> czyste = new ArrayList<>();
        for (String t : teksty) {
            String c = of(t);
            if (c != null) {
                czyste.add(c);
            }
        }
        List<String> klucze = czyste.stream().map(PlainText::klucz).toList();
        List<String> wynik = new ArrayList<>();
        for (int i = 0; i < czyste.size(); i++) {
            boolean powtorzony = false;
            for (int j = 0; j < czyste.size() && !powtorzony; j++) {
                if (j != i) {
                    String ki = klucze.get(i);
                    String kj = klucze.get(j);
                    powtorzony = kj.equals(ki) ? j < i : kj.contains(ki);
                }
            }
            if (!powtorzony) {
                wynik.add(czyste.get(i));
            }
        }
        return wynik.isEmpty() ? null : String.join("\n\n", wynik);
    }

    /** Najwyzej {@code max} znakow - ucinane na granicy slowa, z wielokropkiem. */
    static String cut(String tekst, int max) {
        if (tekst == null || tekst.length() <= max) {
            return tekst;
        }
        int granica = Math.max(tekst.lastIndexOf(' ', max - 1), tekst.lastIndexOf('\n', max - 1));
        return tekst.substring(0, granica > max / 2 ? granica : max - 1).stripTrailing() + "…";
    }

    private static String klucz(String tekst) {
        return tekst.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
