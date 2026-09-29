package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MailService - kiedy poczta jest wlaczona")
class MailServiceTest {

    private static ObjectProvider<JavaMailSender> nadajnik() {
        JavaMailSender sender = new JavaMailSenderImpl();
        return new ObjectProvider<>() {
            @Override public JavaMailSender getObject(Object... args) { return sender; }
            @Override public JavaMailSender getIfAvailable() { return sender; }
            @Override public JavaMailSender getIfUnique() { return sender; }
            @Override public JavaMailSender getObject() { return sender; }
        };
    }

    @Test
    @DisplayName("bez MAIL_HOST poczta jest wylaczona - i to nie jest blad")
    void noHostMeansDisabled() {
        assertThat(new MailService(nadajnik(), "", "", Runnable::run).configured()).isFalse();
    }

    @Test
    @DisplayName("z MAIL_HOST i poprawnym nadawca - wlaczona")
    void hostAndSender() {
        assertThat(new MailService(nadajnik(), "smtp.example.com", "MusicClub <a@b.pl>", Runnable::run).configured())
            .isTrue();
        assertThat(new MailService(nadajnik(), "smtp.example.com", "a@b.pl", Runnable::run).configured()).isTrue();
    }

    @Test
    @DisplayName("MAIL_HOST z nadawca, ktory nie jest adresem (np. login \"apikey\") - serwer nie startuje")
    void invalidSenderFailsFast() {
        assertThatThrownBy(() -> new MailService(nadajnik(), "smtp.sendgrid.net", "apikey", Runnable::run))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> new MailService(nadajnik(), "smtp.example.com", "", Runnable::run))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");
    }
}
