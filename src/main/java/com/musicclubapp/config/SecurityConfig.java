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

/** Konfiguracja Spring Security - wymaganie nr 15. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Klucz do podpisywania ciasteczka "zapamietaj mnie". */
    @Value("${app.remember-me.key:musicclub-dev-key-zmien-mnie}")
    private String rememberMeKey;

    @Value("${app.remember-me.validity-seconds:1209600}") // 14 dni
    private int rememberMeValiditySeconds;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String corsAllowedOrigins;

    /** Serce konfiguracji - opisuje, co dzieje sie z kazdym przychodzacym zapytaniem. */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RememberMeServices rememberMeServices,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
            /* CORS - przegladarka domyslnie blokuje zapytania z innego adresu niz serwer. */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * CSRF - ochrona przed tym, ze obca strona wysle zapytanie w imieniu zalogowanego
             * uzytkownika (przegladarka sama dolaczy ciasteczko sesji).
             */
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfTokenRequestHandler()))

            /* Sesja tworzona dopiero, gdy jest potrzebna (czyli przy logowaniu). */
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                // Ochrona przed przejeciem sesji: po zalogowaniu identyfikator
                // sesji jest zmieniany na nowy.
                .sessionFixation(fixation -> fixation.changeSessionId()))

            /* Kto ma dostep do czego. Kolejnosc ma znaczenie - pierwsza pasujaca regula wygrywa. */
            .authorizeHttpRequests(auth -> auth
                // rejestracja, logowanie i pobranie tokenu CSRF - dla wszystkich
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf",
                    "/api/auth/verify-email", "/api/auth/resend-verification").permitAll()
                /* Wgrane obrazki. */
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()

                // dokumentacja API (wymaganie nr 24) - zeby dalo sie ja pokazac na obronie
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                /*
                 * Sprawdzenie zdrowia aplikacji - uzywa go Docker (healthcheck w
                 * docker-compose.yml), zeby wiedziec, kiedy backend jest gotowy przyjmowac
                 * zapytania.
                 */
                .requestMatchers("/actuator/health").permitAll()

                /* AUTORYZACJA - wyklad 7, slajdy 47-48. */
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // Wlasny profil - kazdy zalogowany, ale tylko swoj (patrz ProfileController)
                .requestMatchers("/api/profile/**").authenticated()

                /*
                 * Publiczne profile innych uzytkownikow (liczba mnoga!). "Publiczne" znaczy tu
                 * "widoczne dla kazdego ZALOGOWANEGO", a nie dla calego internetu - z ulicy nie da
                 * sie przegladac, kto korzysta z serwisu.
                 */
                .requestMatchers("/api/profiles/**").authenticated()

                /* Czat. */
                .requestMatchers("/api/messages/**").authenticated()

                /* Zgloszenia. */
                .requestMatchers("/api/reports/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/reports/**").authenticated()
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
             * Bez tego niezalogowany klient dostaje przekierowanie na formularz logowania (HTTP
             * 302).
             */
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .securityContext(context -> context
                .securityContextRepository(securityContextRepository));

        return http.build();
    }

    /** Sposob odczytu tokenu CSRF z przychodzacego zapytania. */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // null = token wyliczany od razu, a nie leniwie przy pierwszym uzyciu
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    /** BCrypt - standard do hashowania hasel. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Menedzer uwierzytelniania - uzywa go nasz kontroler logowania. */
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    /** Obsluga "zapamietaj mnie" (wymaganie nr 17). */
    @Bean
    public JsonRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        JsonRememberMeServices services =
            new JsonRememberMeServices(rememberMeKey, userDetailsService);

        services.setTokenValiditySeconds(rememberMeValiditySeconds);
        services.setParameter("rememberMe");
        return services;
    }

    /** Miejsce, w ktorym trzymany jest zalogowany uzytkownik - czyli sesja HTTP. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Ustawienia CORS. setAllowCredentials(true) jest konieczne, zeby przegladarka w ogole wyslala
     * ciasteczko sesji na inny port.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(url -> !url.isEmpty())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        /* Naglowki, ktore przegladarka moze ODCZYTAC. */
        config.setExposedHeaders(List.of("X-Current-User"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
