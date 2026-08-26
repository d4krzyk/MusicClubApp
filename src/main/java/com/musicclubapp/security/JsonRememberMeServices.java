package com.musicclubapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * Obsluga "zapamietaj mnie" dla logowania przez JSON - wymaganie nr 17.
 *
 * <p><b>Po co ta klasa?</b> Wyklad 7 (slajdy 44-46) pokazuje "zapamietaj mnie"
 * dla klasycznego formularza HTML - tam przegladarka wysyla pole
 * {@code <input type="checkbox" name="remember-me">} jako parametr zapytania,
 * a Spring sam je znajduje.</p>
 *
 * <p>My mamy API REST i logowanie idzie JSON-em. Metoda
 * {@code loginSuccess()} z klasy nadrzednej szuka wtedy parametru
 * {@code request.getParameter("rememberMe")}, ktorego w ciele JSON po prostu
 * nie ma - wiec uznaje, ze uzytkownik nie zaznaczyl opcji, i cicho nie ustawia
 * ciasteczka. Objawia sie to tak, ze wszystko dziala, tylko funkcja
 * "zapamietaj mnie" nic nie robi.</p>
 *
 * <p>Rozwiazanie: udostepniamy chroniona metode {@code onLoginSuccess()},
 * ktora wystawia ciasteczko bez sprawdzania parametrow zapytania. Decyzje
 * "czy zapamietac" podejmuje kontroler na podstawie pola z JSON-a.</p>
 */
public class JsonRememberMeServices extends TokenBasedRememberMeServices {

    public JsonRememberMeServices(String key, UserDetailsService userDetailsService) {
        // Konstruktor z jawnym algorytmem - wersja dwuargumentowa jest
        // oznaczona jako @Deprecated, a wymaganie nr 15 tego zabrania.
        super(key, userDetailsService, RememberMeTokenAlgorithm.SHA256);
    }

    /**
     * Wystawia ciasteczko "zapamietaj mnie" niezaleznie od parametrow zapytania.
     *
     * @param authentication wynik udanego logowania; jego {@code principal}
     *                       musi byc typu {@code UserDetails}
     */
    public void rememberUser(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) {
        onLoginSuccess(request, response, authentication);
    }
}
