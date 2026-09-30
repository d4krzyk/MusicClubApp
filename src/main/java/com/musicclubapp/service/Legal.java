package com.musicclubapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Regulamin i polityka prywatnosci: wersja dokumentow i dane administratora danych.
 *
 * <p>Dane administratora (imie i nazwisko albo firma, adres kontaktowy) NIE leza w repozytorium -
 * wpisuje je wlasciciel serwisu w zmiennych LEGAL_CONTROLLER i LEGAL_CONTACT_EMAIL. Polityka
 * prywatnosci na stronie pokazuje je z serwera, wiec zmiana nie wymaga przebudowy aplikacji.</p>
 *
 * <p>Wersja to data ostatniej zmiany tresci dokumentow. Kazda zmiana tekstu w
 * {@code frontend/src/legal/} wymaga podniesienia {@code app.legal.version} - inaczej nikt nie
 * dostanie prosby o ponowna akceptacje.</p>
 */
@Component
public class Legal {

    private static final Logger log = LoggerFactory.getLogger(Legal.class);

    private final String version;
    private final String controller;
    private final String contact;
    private final String hosting;
    private final String mailProvider;

    public Legal(@Value("${app.legal.version:2026-09-30}") String version,
                 @Value("${app.legal.controller:}") String controller,
                 @Value("${app.legal.contact-email:}") String contact,
                 @Value("${app.legal.hosting:}") String hosting,
                 @Value("${app.legal.mail-provider:}") String mailProvider) {
        this.version = version.trim();
        this.controller = controller == null ? "" : controller.trim();
        this.contact = contact == null ? "" : contact.trim();
        this.hosting = hosting == null ? "" : hosting.trim();
        this.mailProvider = mailProvider == null ? "" : mailProvider.trim();
    }

    public String version() {
        return version;
    }

    /** Kto jest administratorem danych - pusty, dopoki wlasciciel tego nie ustawi. */
    public String controller() {
        return controller;
    }

    public String contact() {
        return contact;
    }

    /** Kto prowadzi serwer (nazwa dostawcy hostingu) - do punktu "komu przekazujemy dane". */
    public String hosting() {
        return hosting;
    }

    /** Przez kogo wysylamy poczte (np. Google, Brevo) - jw. */
    public String mailProvider() {
        return mailProvider;
    }

    /**
     * Polityka prywatnosci bez administratora i kontaktu jest niepelna - RODO wymaga podania obu.
     * Nie zatrzymujemy przez to serwera (lokalnie i na testach nikt ich nie ustawia), ale przy
     * kazdym starcie mowimy o tym w logu.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warnWhenIncomplete() {
        if (!StringUtils.hasText(controller) || !StringUtils.hasText(contact)) {
            log.warn("Polityka prywatnosci: brak danych administratora - ustaw LEGAL_CONTROLLER i "
                + "LEGAL_CONTACT_EMAIL, zanim wpuscisz na serwer prawdziwych uzytkownikow.");
        }
        if (!StringUtils.hasText(hosting) || !StringUtils.hasText(mailProvider)) {
            log.warn("Polityka prywatnosci: brak nazw dostawcow - ustaw LEGAL_HOSTING (kto prowadzi serwer) "
                + "i LEGAL_MAIL_PROVIDER (przez kogo idzie poczta); polityka pokaze w tych miejscach "
                + "prosbe o uzupelnienie.");
        }
    }
}
