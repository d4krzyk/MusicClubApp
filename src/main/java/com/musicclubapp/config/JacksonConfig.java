package com.musicclubapp.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Ustala, jak daty wychodza z API - i to jest naprawa realnego bledu.
 *
 * <p><b>Jak sie objawial.</b> Osoba, ktora byla aktywna przed chwila, miala
 * przy nazwisku napis <i>„aktywny 2 godziny temu"</i>. Wiadomosc wyslana
 * przed sekunda dostawala w czacie godzine sprzed dwoch godzin. Przesuniecie
 * bylo zawsze takie samo i rowne roznicy miedzy czasem polskim a UTC.</p>
 *
 * <p><b>Skad sie bralo.</b> Encje trzymaja {@link LocalDateTime}, czyli date
 * <b>bez strefy czasowej</b>, i domyslnie tak tez trafiala ona do JSON-a:</p>
 *
 * <pre>
 * "createdAt": "2026-08-28T12:00:00"
 * </pre>
 *
 * <p>Serwer zapisal tu godzine w UTC, ale w samym tekscie nie ma o tym ani
 * slowa. Norma jezyka JavaScript mowi, ze date-czas <b>bez</b> przesuniecia
 * czyta sie jako czas <b>lokalny</b> - wiec przegladarka w Polsce rozumiala
 * to jako 12:00 czasu warszawskiego, czyli 10:00 UTC. Kazdy znacznik czasu
 * byl przez to cofniety o dwie godziny.</p>
 *
 * <p><b>Dlaczego to nie wyszlo wczesniej.</b> Testy porownuja daty po stronie
 * Javy, gdzie obie wartosci sa tym samym {@code LocalDateTime} - przesuniecie
 * pojawia sie dopiero przy przejsciu przez JSON do przegladarki. Co gorsza,
 * gdy serwer i przegladarka stoja w tej samej strefie, blad znika calkowicie:
 * na komputerze do nauki wszystko wygladalo dobrze.</p>
 *
 * <p><b>Naprawa.</b> Do wysylanej daty dopisujemy {@code Z}, czyli mowimy
 * wprost: <i>to jest UTC</i>. Przegladarka nie musi juz niczego zgadywac
 * i sama pokazuje czas lokalny. Druga polowa naprawy jest w
 * {@code MusicClubAppApplication#main} - tam przypinamy zegar serwera do UTC,
 * zeby dopisywane {@code Z} bylo prawda takze wtedy, gdy aplikacja chodzi
 * na komputerze ustawionym na Warszawe.</p>
 *
 * <p><b>Czego to NIE zmienia.</b> W bazie nadal siedzi {@code LocalDateTime}
 * i zadna kolumna sie nie zmienia. Zmiana dotyczy wylacznie tego, co
 * aplikacja <i>mowi</i> o zapisanych datach - wczesniej nie mowila nic.</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * Format wyjsciowy: {@code 2026-08-28T12:00:00Z}.
     *
     * <p>Sekundy ulamkowe pomijamy celowo. Do niczego w tej aplikacji nie sa
     * potrzebne (najkrotszy odstep, jaki pokazujemy, to sekunda), a skracaja
     * odpowiedzi i czynia je czytelniejszymi w narzedziach deweloperskich.</p>
     */
    private static final DateTimeFormatter UTC_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcDates() {
        return builder -> builder.serializerByType(LocalDateTime.class, new UtcLocalDateTime());
    }

    /** Zapisuje {@link LocalDateTime} jako czas UTC z jawnym {@code Z} na koncu. */
    private static final class UtcLocalDateTime extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator,
                              SerializerProvider serializers) throws IOException {
            /*
             * Zadnego przeliczania stref tu nie ma i byc nie moze: wartosc
             * JEST juz w UTC (patrz TimeZone.setDefault przy starcie).
             * Doliczanie czegokolwiek w tym miejscu przesunelo by daty
             * drugi raz - tym razem w druga strone.
             */
            generator.writeString(value.atOffset(ZoneOffset.UTC).format(UTC_FORMAT));
        }
    }
}
