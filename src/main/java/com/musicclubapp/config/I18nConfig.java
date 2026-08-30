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

/** Obsluga dwoch jezykow - wymaganie nr 2. */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private final MessageSource messageSource;

    public I18nConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Jezyki, ktore obslugujemy. Kolejnosc ma znaczenie - pierwszy jest domyslny. */
    private static final List<Locale> SUPPORTED_LANGUAGES =
        List.of(Locale.ENGLISH, Locale.forLanguageTag("pl"));

    /** Skad brac jezyk dla danego zapytania. */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("LANG");
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        resolver.setDefaultLocaleFunction(this::languageFromHeader);
        return resolver;
    }

    /**
     * Dobiera najlepszy pasujacy jezyk na podstawie naglowka Accept-Language (np.
     * "pl-PL,pl;q=0.9,en;q=0.8").
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

    /** Pozwala wymusic jezyk parametrem w adresie: ?lang=pl. */
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

    /** Spina Bean Validation z plikami messages. */
    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    /** Podstawia nasz walidator pod mechanizm Spring MVC. */
    @Override
    public org.springframework.validation.Validator getValidator() {
        return validator();
    }
}
