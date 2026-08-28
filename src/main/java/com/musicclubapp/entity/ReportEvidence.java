package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

/**
 * Jedna linijka dowodu dolaczona do zgloszenia - <b>migawka, nie odnosnik</b>.
 *
 * <p>To jest najwazniejsza decyzja w calym systemie zgloszen, wiec warto
 * ja uzasadnic dokladnie.</p>
 *
 * <p><b>Dlaczego kopia tresci, a nie klucz obcy do wiadomosci.</b> Gdyby
 * zgloszenie tylko WSKAZYWALO na wiadomosci, osoba zglaszana mialaby prosty
 * sposob na wyjscie z sytuacji: skasowac konto (co kasuje jej wiadomosci)
 * albo poczekac, az zrobi to sam zglaszajacy. Administrator otwieralby wtedy
 * zgloszenie i widzial pustke. Migawka jest odporna na to z definicji -
 * zapisujemy, co bylo napisane W CHWILI ZGLOSZENIA.</p>
 *
 * <p><b>Dlaczego migawka, a nie prawo administratora do czytania rozmow.</b>
 * Druga mozliwa droga to endpoint "pokaz mi rozmowe tych dwoch osob".
 * Byloby to znacznie potezniejsze uprawnienie: administrator moglby wtedy
 * czytac dowolna rozmowe w serwisie, kiedy chce. Tutaj widzi wylacznie to,
 * co zglaszajacy sam mu pokazal - czyli fragment WLASNEJ rozmowy, swiadomie
 * udostepniony. Zakres jest zamkniety i wynika ze zgody uzytkownika.</p>
 *
 * <p><b>Klasa osadzona</b> ({@code @Embeddable}), bo linijka dowodu nie ma
 * wlasnego zycia: istnieje tylko jako czesc zgloszenia i ginie razem z nim.
 * Osobna encja z kluczem glownym sugerowalaby, ze mozna sie do niej odwolac
 * skadinad - a nie mozna i nie powinno byc mozna.</p>
 */
@Embeddable
public class ReportEvidence {

    /** Kto to napisal - sam login, bo tresc ma przetrwac kasowanie konta. */
    @Column(name = "author", nullable = false, length = 50)
    private String author;

    /** Co napisal; przy samym nagraniu - jego tytul. */
    @Column(name = "text", length = 2000, columnDefinition = "TEXT")
    private String text;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected ReportEvidence() {
    }

    public ReportEvidence(String author, String text, LocalDateTime sentAt) {
        this.author = author;
        this.text = text;
        this.sentAt = sentAt;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
