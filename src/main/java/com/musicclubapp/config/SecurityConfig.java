package com.musicclubapp.config;

import com.musicclubapp.security.JsonRememberMeServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Konfiguracja Spring Security - wymaganie nr 15.
 *
 * <p>Napisane wedlug wykladu 7. Slajd 31 opisuje dokladnie nasza sytuacje:
 * dla aplikacji REST sa dwa podejscia - token (JWT) albo ciasteczka (sesja).
 * Wybralismy sesje, a wyklad zaznacza, ze wymaga to "stworzenia wlasnego
 * AuthenticationManager i recznego przebiegu logowania" - i tak wlasnie
 * to tu wyglada.</p>
 *
 * <p><b>Wymaganie nr 15 mowi wprost, ze konfiguracja NIE moze byc
 * "deprecated"</b>. Dwie rzeczy, na ktore trzeba uwazac:</p>
 * <ul>
 *   <li>Stare tutoriale kaza dziedziczyc po {@code WebSecurityConfigurerAdapter}.
 *       Ta klasa zostala usunieta w Spring Security 6 - dzis definiuje sie
 *       bean typu {@link SecurityFilterChain}, tak jak nizej.</li>
 *   <li>Wewnatrz uzywamy skladni lambda ({@code http.csrf(csrf -> ...)}).
 *       Stary lancuchowy zapis ({@code http.csrf().disable().and()...})
 *       jest oznaczony jako przestarzaly.</li>
 * </ul>
 *
 * <p><b>Sposob logowania: sesja + ciasteczko.</b> Po zalogowaniu serwer
 * zapamietuje uzytkownika w sesji, a przegladarka dostaje ciasteczko
 * {@code JSESSIONID} i odsyla je przy kazdym kolejnym zapytaniu.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Klucz do podpisywania ciasteczka "zapamietaj mnie". W prawdziwym projekcie
     * czyta sie go ze zmiennej srodowiskowej - wartosc domyslna jest tylko po to,
     * zeby aplikacja wstala na komputerze do nauki.
     */
    @Value("${app.remember-me.key:musicclub-dev-key-zmien-mnie}")
    private String rememberMeKey;

    @Value("${app.remember-me.validity-seconds:1209600}") // 14 dni
    private int rememberMeValiditySeconds;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String corsAllowedOrigins;

    /**
     * Serce konfiguracji - opisuje, co dzieje sie z kazdym przychodzacym zapytaniem.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RememberMeServices rememberMeServices,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
            /*
             * CORS - przegladarka domyslnie blokuje zapytania z innego adresu
             * niz serwer. Frontend React chodzi na porcie 5173, backend na 8080,
             * wiec musimy jawnie na to pozwolic.
             */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * CSRF - ochrona przed tym, ze obca strona wysle zapytanie w imieniu
             * zalogowanego uzytkownika (przegladarka sama dolaczy ciasteczko sesji).
             *
             * Przy logowaniu sesyjnym TEGO NIE WOLNO WYLACZAC - w tutorialach
             * czesto widac ".csrf(csrf -> csrf.disable())", ale to bezpieczne
             * dopiero przy tokenach JWT, gdzie przegladarka nic sama nie dolacza.
             *
             * withHttpOnlyFalse() pozwala JavaScriptowi odczytac ciasteczko
             * XSRF-TOKEN i odeslac je w naglowku X-XSRF-TOKEN. Tak wlasnie
             * dziala axios i fetch po stronie Reacta.
             */
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfTokenRequestHandler()))

            /*
             * Sesja tworzona dopiero, gdy jest potrzebna (czyli przy logowaniu).
             * Anonimowe zapytania nie zajmuja pamieci serwera.
             */
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                // Ochrona przed przejeciem sesji: po zalogowaniu identyfikator
                // sesji jest zmieniany na nowy.
                .sessionFixation(fixation -> fixation.changeSessionId()))

            /* Kto ma dostep do czego. Kolejnosc ma znaczenie - pierwsza pasujaca regula wygrywa. */
            .authorizeHttpRequests(auth -> auth
                // rejestracja, logowanie i pobranie tokenu CSRF - dla wszystkich
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                /*
                 * Wgrane obrazki. Przegladarka pobiera je zwyklym <img src="...">,
                 * bez naglowkow i bez sesji - gdyby wymagaly logowania, w tablicy
                 * zamiast zdjec bylyby puste ramki.
                 */
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()

                // dokumentacja API (wymaganie nr 24) - zeby dalo sie ja pokazac na obronie
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                /*
                 * Sprawdzenie zdrowia aplikacji - uzywa go Docker (healthcheck
                 * w docker-compose.yml), zeby wiedziec, kiedy backend jest
                 * gotowy przyjmowac zapytania.
                 *
                 * Docker nie ma jak sie zalogowac, wiec ten jeden adres musi
                 * byc otwarty. Nie zdradza niczego wrazliwego: odpowiedzia jest
                 * samo {"status":"UP"} (patrz management.endpoint.health
                 * .show-details=never w application.properties).
                 */
                .requestMatchers("/actuator/health").permitAll()

                /*
                 * AUTORYZACJA - wyklad 7, slajdy 47-48.
                 *
                 * Przegladanie listy wszystkich kont to funkcja administracyjna.
                 * Zwykly uzytkownik nie ma powodu widziec, kto jeszcze korzysta
                 * z serwisu, ani ogladac cudzych adresow e-mail.
                 *
                 * Wyklad zaleca wlasnie ten sposob (regula w konfiguracji)
                 * zamiast adnotacji @PreAuthorize przy metodach - "bo
                 * konfiguracja jest w jednym miejscu" (slajd 47).
                 *
                 * hasRole("ADMIN") sprawdza uprawnienie "ROLE_ADMIN" -
                 * przedrostek ROLE_ Spring dokleja sam, dlatego w kodzie
                 * podajemy sama nazwe roli.
                 */
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // Wlasny profil - kazdy zalogowany, ale tylko swoj (patrz ProfileController)
                .requestMatchers("/api/profile/**").authenticated()

                /*
                 * Publiczne profile innych uzytkownikow (liczba mnoga!).
                 * "Publiczne" znaczy tu "widoczne dla kazdego ZALOGOWANEGO",
                 * a nie dla calego internetu - z ulicy nie da sie przegladac,
                 * kto korzysta z serwisu.
                 *
                 * Regule pisemy jawnie, mimo ze anyRequest() ponizej zrobilby
                 * to samo: przy nastepnej zmianie widac wtedy od razu, ze to
                 * decyzja, a nie przeoczenie.
                 */
                .requestMatchers("/api/profiles/**").authenticated()
                // zapytania OPTIONS wysyla sama przegladarka przed wlasciwym zapytaniem (CORS preflight)
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // cala reszta wymaga zalogowania
                .anyRequest().authenticated())

            /* "Zapamietaj mnie" - wymaganie nr 17. */
            .rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices))

            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                // Domyslnie Spring przekierowuje na strone logowania. My jestesmy
                // API, wiec zamiast przekierowania odsylamy czysty status 204.
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value())))

            /*
             * Bez tego niezalogowany klient dostaje przekierowanie na formularz
             * logowania (HTTP 302). Frontend REST-owy oczekuje kodu 401,
             * zeby wiedziec, ze trzeba pokazac ekran logowania.
             */
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .securityContext(context -> context
                .securityContextRepository(securityContextRepository));

        return http.build();
    }

    /**
     * Sposob odczytu tokenu CSRF z przychodzacego zapytania.
     *
     * <p><b>Po co to nadpisujemy?</b> Domyslnie Spring Security 6 uzywa
     * {@code XorCsrfTokenRequestAttributeHandler}, ktory maskuje token
     * operacja XOR (ochrona przed atakiem BREACH). Problem w tym, ze
     * w ciasteczku XSRF-TOKEN siedzi token <i>surowy</i>. Frontend odczytuje
     * ciasteczko i odsyla je jako naglowek, a handler XOR oczekuje wersji
     * zamaskowanej - i odrzuca zapytanie. Objawia sie to tym, ze KAZDY POST
     * konczy sie odmowa, mimo poprawnie ustawionego ciasteczka.</p>
     *
     * <p>Zwykly {@code CsrfTokenRequestAttributeHandler} porownuje wartosc
     * z naglowka wprost z ta z ciasteczka, co dziala z frontendem w stylu
     * React/axios. Tak wlasnie ten przypadek opisuje dokumentacja Spring
     * Security w rozdziale o aplikacjach jednostronicowych.</p>
     */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // null = token wyliczany od razu, a nie leniwie przy pierwszym uzyciu
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    /**
     * BCrypt - standard do hashowania hasel.
     *
     * <p>Do bazy trafia hash, nigdy samo haslo. BCrypt celowo liczy sie wolno
     * i dokleja losowa "sol", wiec dwa takie same hasla daja rozne hashe -
     * to psuje ataki z gotowych tablic.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Menedzer uwierzytelniania - uzywa go nasz kontroler logowania.
     *
     * <p>Skladamy go jawnie, dokladnie jak na wykladzie 7 (slajd 34):</p>
     * <ul>
     *   <li>{@code DaoAuthenticationProvider} - sprawdza login i haslo
     *       korzystajac z bazy danych,</li>
     *   <li>dostaje nasz {@code AppUserDetailsService} (skad wziac uzytkownika)
     *       oraz {@link #passwordEncoder()} (jak porownac haslo z hashem),</li>
     *   <li>{@code ProviderManager} opakowuje providery w jeden menedzer.</li>
     * </ul>
     *
     * <p>Da sie krocej ({@code AuthenticationConfiguration.getAuthenticationManager()}),
     * ale wtedy nie widac, co sie w srodku dzieje - a tu chodzi o to, zeby bylo
     * widac, ktory element za co odpowiada.</p>
     */
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    /**
     * Obsluga "zapamietaj mnie" (wymaganie nr 17).
     *
     * <p>Po zaznaczeniu tej opcji przegladarka dostaje dodatkowe ciasteczko
     * wazne 14 dni. Gdy sesja wygasnie, Spring rozpozna to ciasteczko
     * i zaloguje uzytkownika ponownie bez pytania o haslo.</p>
     *
     * <p>Uzywamy wlasnej klasy {@link JsonRememberMeServices}, bo logujemy
     * sie JSON-em, a nie formularzem HTML - szczegoly w komentarzu tamtej klasy.</p>
     */
    @Bean
    public JsonRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        JsonRememberMeServices services =
            new JsonRememberMeServices(rememberMeKey, userDetailsService);

        services.setTokenValiditySeconds(rememberMeValiditySeconds);
        services.setParameter("rememberMe");
        return services;
    }

    /**
     * Miejsce, w ktorym trzymany jest zalogowany uzytkownik - czyli sesja HTTP.
     * Potrzebujemy go jako beana, bo nasz kontroler logowania zapisuje tam
     * uzytkownika recznie po udanym sprawdzeniu hasla.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Ustawienia CORS.
     *
     * <p>{@code setAllowCredentials(true)} jest konieczne, zeby przegladarka
     * w ogole wyslala ciasteczko sesji na inny port. Przy wlaczonym
     * {@code allowCredentials} nie wolno uzyc gwiazdki w dozwolonych adresach -
     * trzeba wypisac je konkretnie, stad lista z application.properties.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(adres -> !adres.isEmpty())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
