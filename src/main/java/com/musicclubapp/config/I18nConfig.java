package com.musicclubapp.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Obsluga dwoch jezykow - wymaganie nr 2. Zbudowane wedlug wykladu 2
 * (slajdy 41-45).
 *
 * <p>Wyklad 2 na slajdzie 46 zauwaza, ze "nie ma potrzeby zajmowac sie
 * wsparciem dla jezykow w aplikacji REST" - bo teksty menu sa po stronie
 * frontendu. To prawda w polowie: <b>komunikaty bledow walidacji generuje
 * backend</b>, a wymaganie nr 2 wymienia je jako pierwszy przyklad. Dlatego
 * dzielimy to na dwie czesci:</p>
 * <ul>
 *   <li><b>backend (tutaj)</b> - komunikaty bledow walidacji i wyjatkow,</li>
 *   <li><b>frontend (KROK 5)</b> - menu i etykiety, biblioteka i18next.</li>
 * </ul>
 *
 * <p>Klasa implementuje {@link WebMvcConfigurer} i ma {@code @Configuration},
 * dokladnie jak mowi slajd 45.</p>
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private final MessageSource messageSource;

    public I18nConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Jezyki, ktore obslugujemy. Kolejnosc ma znaczenie - pierwszy jest domyslny. */
    private static final List<Locale> SUPPORTED_LANGUAGES =
        List.of(Locale.ENGLISH, Locale.forLanguageTag("pl"));

    /**
     * Skad brac jezyk dla danego zapytania.
     *
     * <p>Wyklad 2 (slajd 43) wymienia cztery implementacje: sesyjna,
     * ciasteczkowa, z naglowka HTTP i stala. Wybralismy
     * {@link CookieLocaleResolver}, bo laczy dwie rzeczy naraz:</p>
     * <ul>
     *   <li>gdy uzytkownik nic nie wybral - jezyk bierzemy z naglowka
     *       {@code Accept-Language}, ktory przegladarka wysyla sama
     *       (to metoda {@code setDefaultLocaleFunction} nizej),</li>
     *   <li>gdy uzytkownik kliknie przelacznik PL/EN - wybor zapisuje sie
     *       w ciasteczku i dziala przy kolejnych zapytaniach.</li>
     * </ul>
     *
     * <p><b>Dlaczego nie {@link AcceptHeaderLocaleResolver}?</b> Wydaje sie
     * naturalniejszy dla REST API, ale nie da sie go polaczyc
     * z {@link LocaleChangeInterceptor} ze slajdu 44. Interceptor probuje
     * ustawic jezyk metoda {@code setLocale()}, a ten resolver rzuca wtedy
     * {@code UnsupportedOperationException} ("Cannot change HTTP Accept-Language
     * header") - kazde zapytanie z {@code ?lang=pl} konczy sie bledem 500.</p>
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("LANG");
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        resolver.setDefaultLocaleFunction(this::languageFromHeader);
        return resolver;
    }

    /**
     * Dobiera najlepszy pasujacy jezyk na podstawie naglowka
     * {@code Accept-Language} (np. {@code "pl-PL,pl;q=0.9,en;q=0.8"}).
     * Gdy naglowka nie ma albo prosi o jezyk, ktorego nie mamy - angielski.
     */
    private Locale languageFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            Locale matched = Locale.lookup(
                Locale.LanguageRange.parse(header), SUPPORTED_LANGUAGES);
            return matched != null ? matched : Locale.ENGLISH;
        } catch (IllegalArgumentException e) {
            // Naglowek moze byc zepsuty (przysyla go klient) - wtedy po prostu
            // wracamy do domyslnego jezyka zamiast wywracac cale zapytanie.
            return Locale.ENGLISH;
        }
    }

    /**
     * Pozwala wymusic jezyk parametrem w adresie: {@code ?lang=pl}.
     * Wyklad 2, slajd 44. Bardzo sie przydaje przy pokazywaniu projektu -
     * nie trzeba przestawiac jezyka calej przegladarki, zeby pokazac
     * komunikaty po polsku.
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    /** Rejestracja interceptora - slajd 45. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    /**
     * Spina Bean Validation z plikami messages.
     *
     * <p>Bez tego beana komunikaty w klamrach (np.
     * {@code "{validation.email.invalid}"}) szukane sa w osobnym pliku
     * {@code ValidationMessages.properties}. Po tym podpieciu leca do tych
     * samych plikow {@code lang/messages*.properties} co reszta tekstow -
     * jedno miejsce na wszystkie komunikaty zamiast dwoch.</p>
     */
    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    /**
     * Podstawia nasz walidator pod mechanizm Spring MVC.
     *
     * <p>Samo zdefiniowanie beana wyzej NIE wystarczy - Spring MVC uzywa
     * wlasnego walidatora i nadal szukalby tekstow w domyslnym pliku
     * {@code ValidationMessages.properties}. Dopiero nadpisanie tej metody
     * z {@link WebMvcConfigurer} sprawia, ze komunikaty w klamrach
     * (np. {@code "{validation.email.invalid}"}) sa tlumaczone z naszych
     * plikow. Bez tego w odpowiedzi JSON widac surowy klucz w klamrach
     * zamiast tekstu bledu.</p>
     */
    @Override
    public org.springframework.validation.Validator getValidator() {
        return validator();
    }
}
