package com.musicclubapp.security;

import com.musicclubapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Wylogowuje sesje, ktorych znacznik bezpieczenstwa konta jest nieaktualny.
 *
 * <p>Sesja zapamietuje znacznik przy logowaniu. Zmiana hasla, reset hasla,
 * "wyloguj z innych urzadzen" i "to nie ja" przy zmianie adresu zmieniaja
 * znacznik w bazie - i przy nastepnym zapytaniu kazda inna sesja tego konta
 * trafia tutaj na niezgodnosc i przestaje byc zalogowana.</p>
 *
 * <p>Sesja bez zapamietanego znacznika (logowanie ciasteczkiem "zapamietaj
 * mnie" albo sesja sprzed tej funkcji) dostaje obecny. Ciasteczko i tak
 * ma znacznik w podpisie, wiec po zmianie znacznika juz nie zaloguje.</p>
 */
public class SecurityStampFilter extends OncePerRequestFilter {

    /** Pod tym kluczem sesja trzyma znacznik z chwili logowania. */
    public static final String ATTR = "musicclub.securityStamp";

    private final UserRepository users;

    public SecurityStampFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        /*
         * Bez sesji nie ma czego uniewazniac: pierwsze zapytanie po logowaniu
         * ciasteczkiem "zapamietaj mnie" sesje dopiero tworzy, a ciasteczko ma
         * znacznik w podpisie, wiec niewazne i tak by nie przeszlo.
         */
        if (session != null && auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)) {
            Optional<String> obecny = users.securityStampOf(auth.getName());
            Object zapamietany = session.getAttribute(ATTR);
            if (obecny.isEmpty()) {
                // Konta juz nie ma
                wyloguj(session);
            } else if (zapamietany == null) {
                session.setAttribute(ATTR, obecny.get());
            } else if (!zapamietany.equals(obecny.get())) {
                wyloguj(session);
            }
        }
        chain.doFilter(request, response);
    }

    private static void wyloguj(HttpSession session) {
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
    }
}
