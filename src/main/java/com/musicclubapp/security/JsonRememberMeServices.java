package com.musicclubapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import java.util.function.Function;

/**
 * Obsluga "zapamietaj mnie" dla logowania przez JSON - wymaganie nr 17.
 *
 * <p>Podpis ciasteczka zawiera hash hasla (tak robi Spring) ORAZ znacznik
 * bezpieczenstwa konta. Zmiana hasla uniewaznia ciasteczka juz sama - ale
 * "wyloguj z innych urzadzen" hasla nie zmienia, a ma wylogowac takze
 * urzadzenia zapamietane ciasteczkiem.</p>
 */
public class JsonRememberMeServices extends TokenBasedRememberMeServices {

    private final Function<String, String> stampOf;

    public JsonRememberMeServices(String key, UserDetailsService userDetailsService,
                                  Function<String, String> stampOf) {
        // Konstruktor z jawnym algorytmem - wersja dwuargumentowa jest
        // oznaczona jako @Deprecated, a wymaganie nr 15 tego zabrania.
        super(key, userDetailsService, RememberMeTokenAlgorithm.SHA256);
        this.stampOf = stampOf;
    }

    @Override
    protected String makeTokenSignature(long tokenExpiryTime, String username, String password,
                                        RememberMeTokenAlgorithm algorithm) {
        String znacznik = stampOf.apply(username);
        return super.makeTokenSignature(tokenExpiryTime, username,
            password + ":" + (znacznik == null ? "" : znacznik), algorithm);
    }

    /** Wystawia ciasteczko "zapamietaj mnie" niezaleznie od parametrow zapytania. */
    public void rememberUser(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) {
        onLoginSuccess(request, response, authentication);
    }
}
