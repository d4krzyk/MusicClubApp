package com.musicclubapp.service;

import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wysylka poczty przez SMTP podany w MAIL_HOST i reszcie zmiennych MAIL_*.
 *
 * <p>Bez MAIL_HOST serwer nie wysyla niczego - i wtedy potwierdzanie adresow
 * jest wylaczone, bo nie byloby jak dostac linku.</p>
 *
 * <p>Wysylka idzie w tle i dopiero po zatwierdzeniu transakcji. W tle - bo
 * serwer pocztowy potrafi odpowiadac kilka sekund, a rejestracja nie powinna
 * tyle wisiec. Po zatwierdzeniu - bo gdyby zapis konta sie wycofal, ktos
 * dostalby link do konta, ktorego nie ma.</p>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /** Obrazek w tresci wiadomosci: identyfikator z cid: w HTML-u i plik w zasobach. */
    public record Inline(String contentId, String resource) {
    }

    /** Gotowa wiadomosc - tresc zwykla i HTML, plus obrazki, do ktorych odwoluje sie HTML. */
    public record Mail(String to, String subject, String text, String html, Map<String, Inline> images) {
    }

    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String from;
    private final Executor wysylka;
    private final ExecutorService wlasnyWatek;

    @Autowired
    public MailService(ObjectProvider<JavaMailSender> senders,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from:}") String from) {
        this(senders, host, from, null);
    }

    /** Dla testow: z wykonawca, ktory wysyla od razu, w tym samym watku. */
    MailService(ObjectProvider<JavaMailSender> senders, String host, String from, Executor wysylka) {
        this.senders = senders;
        this.host = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
        if (wysylka == null) {
            this.wlasnyWatek = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "poczta");
                t.setDaemon(true);
                return t;
            });
            this.wysylka = wlasnyWatek;
        } else {
            this.wlasnyWatek = null;
            this.wysylka = wysylka;
        }
        if (StringUtils.hasText(this.host)) {
            sprawdzNadawce(this.from);
            log.info("Poczta: wysylka przez {} jako {}", this.host, this.from);
        }
    }

    /**
     * Jest MAIL_HOST, wiec poczta ma dzialac - a bez poprawnego nadawcy kazda
     * wysylka konczylaby sie bledem w logu, podczas gdy ludzie czekaliby na
     * wiadomosci, ktore nigdy nie wyjda. Lepiej nie wystartowac z jasnym
     * komunikatem. Typowy przypadek: login SMTP, ktory nie jest adresem
     * (SendGrid ma "apikey") - wtedy trzeba ustawic MAIL_FROM.
     */
    private static void sprawdzNadawce(String from) {
        String blad = null;
        if (!StringUtils.hasText(from)) {
            blad = "brak nadawcy";
        } else {
            try {
                InternetAddress adres = new InternetAddress(from, true);
                if (adres.getAddress() == null || !adres.getAddress().contains("@")) {
                    blad = "\"" + from + "\" to nie adres e-mail";
                }
            } catch (AddressException e) {
                blad = "\"" + from + "\" to nie adres e-mail (" + e.getMessage() + ")";
            }
        }
        if (blad != null) {
            throw new IllegalStateException("Poczta: jest MAIL_HOST, ale " + blad
                + ". Ustaw MAIL_FROM, np. MAIL_FROM=\"MusicClub <twoj@adres.pl>\" - albo usun MAIL_HOST.");
        }
    }

    /** Czy serwer ma skad wysylac poczte. */
    public boolean configured() {
        return StringUtils.hasText(host) && StringUtils.hasText(from) && senders.getIfAvailable() != null;
    }

    /**
     * Wysyla wiadomosc, gdy biezaca transakcja sie zatwierdzi (albo od razu,
     * jesli zadnej nie ma). Blad wysylki trafia do logu - nie cofa zalozenia
     * konta, bo link mozna wyslac jeszcze raz.
     */
    public void sendAfterCommit(Mail mail) {
        if (!configured()) {
            return;
        }
        Runnable wyslij = () -> wysylka.execute(() -> send(mail));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wyslij.run();
                }
            });
        } else {
            wyslij.run();
        }
    }

    private void send(Mail mail) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            // multipart: tresc zwykla i HTML jako alternatywy, obrazki jako czesci "related"
            MimeMessageHelper helper = new MimeMessageHelper(message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(nadawca());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            for (Inline obrazek : mail.images().values()) {
                helper.addInline(obrazek.contentId(), new ClassPathResource(obrazek.resource()), "image/png");
            }
            sender.send(message);
            log.info("Poczta: wyslano \"{}\" na {}", mail.subject(), EmailAddresses.mask(mail.to()));
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            // Adres odbiorcy maskujemy - log to nie miejsce na cudze skrzynki
            log.warn("Poczta: nie udalo sie wyslac \"{}\" na {}: {}", mail.subject(),
                EmailAddresses.mask(mail.to()), e.getMessage());
        }
    }

    /** "MusicClub <no-reply@...>" - sam adres dostaje nazwe aplikacji jako podpis. */
    private InternetAddress nadawca() throws AddressException, UnsupportedEncodingException {
        InternetAddress adres = new InternetAddress(from, true);
        if (adres.getPersonal() == null) {
            adres.setPersonal("MusicClub", StandardCharsets.UTF_8.name());
        }
        return adres;
    }

    @PreDestroy
    void zatrzymaj() {
        if (wlasnyWatek != null) {
            wlasnyWatek.shutdown();
        }
    }
}
