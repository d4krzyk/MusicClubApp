package com.musicclubapp.service;

import com.musicclubapp.error.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Adresy e-mail i limit wysylek")
class EmailAddressesTest {

    @Test
    @DisplayName("maskowanie zostawia pierwsza i ostatnia litere nazwy oraz domene")
    void mask() {
        assertThat(EmailAddresses.mask("jan.kowalski@gmail.com")).isEqualTo("j***i@gmail.com");
        assertThat(EmailAddresses.mask("ab@x.pl")).isEqualTo("a***@x.pl");
        assertThat(EmailAddresses.mask("zepsuty")).isEqualTo("***");
    }

    @Test
    @DisplayName("skrzynki jednorazowe - takze poddomeny i wielkie litery")
    void disposable() {
        assertThat(EmailAddresses.isDisposable("a@mailinator.com")).isTrue();
        assertThat(EmailAddresses.isDisposable("a@X.YOPMAIL.COM")).isTrue();
        assertThat(EmailAddresses.isDisposable("a@gmail.com")).isFalse();
        // "mailinator.com.pl" to inna domena niz mailinator.com
        assertThat(EmailAddresses.isDisposable("a@mailinator.com.pl")).isFalse();
    }

    @Test
    @DisplayName("limit na adres IP: po wyczerpaniu 429, po godzinie znowu mozna")
    void ipLimit() {
        AtomicReference<Instant> teraz = new AtomicReference<>(Instant.parse("2026-09-29T10:00:00Z"));
        Clock zegar = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return teraz.get(); }
        };
        MailRateLimiter limiter = new MailRateLimiter(zegar, 3);

        limiter.acquire("1.2.3.4");
        limiter.acquire("1.2.3.4");
        limiter.acquire("1.2.3.4");
        limiter.acquire("5.6.7.8");
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4"))
            .isInstanceOfSatisfying(TooManyRequestsException.class,
                e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(3600));

        teraz.set(teraz.get().plus(Duration.ofMinutes(61)));
        limiter.forgetOld();
        limiter.acquire("1.2.3.4");
    }
}
