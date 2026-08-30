package com.musicclubapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/** Obsluga "zapamietaj mnie" dla logowania przez JSON - wymaganie nr 17. */
public class JsonRememberMeServices extends TokenBasedRememberMeServices {

    public JsonRememberMeServices(String key, UserDetailsService userDetailsService) {
        // Konstruktor z jawnym algorytmem - wersja dwuargumentowa jest
        // oznaczona jako @Deprecated, a wymaganie nr 15 tego zabrania.
        super(key, userDetailsService, RememberMeTokenAlgorithm.SHA256);
    }

    /** Wystawia ciasteczko "zapamietaj mnie" niezaleznie od parametrow zapytania. */
    public void rememberUser(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) {
        onLoginSuccess(request, response, authentication);
    }
}
