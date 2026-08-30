package com.musicclubapp.config;

import com.musicclubapp.service.PresenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Odnotowuje aktywnosc zalogowanego uzytkownika przy kazdym zapytaniu do API. */
@Configuration
public class PresenceWebConfig implements WebMvcConfigurer {

    /** Naglowek z nazwa konta, ktore serwer widzi jako zalogowane. */
    public static final String CURRENT_USER_HEADER = "X-Current-User";

    private final ObjectProvider<PresenceService> presence;

    public PresenceWebConfig(ObjectProvider<PresenceService> presence) {
        this.presence = presence;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PresenceInterceptor(presence))
            .addPathPatterns("/api/**");
    }

    /** Zapisuje "byl tu przed chwila" i przepuszcza zapytanie dalej. */
    private record PresenceInterceptor(ObjectProvider<PresenceService> presence)
        implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) {

            Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

            /*
             * isAuthenticated() nie wystarczy: niezalogowany uzytkownik ma w kontekscie token
             * anonimowy, ktory tez odpowiada "tak", a jego nazwa to "anonymousUser".
             */
            if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {

                PresenceService service = presence.getIfAvailable();
                if (service != null) {
                    service.touch(authentication.getName());
                }

                /* Kto JEST teraz zalogowany - wedlug serwera. */
                response.setHeader(CURRENT_USER_HEADER, authentication.getName());
            }

            return true;   // aktywnosc to notatka na boku, nigdy powod do odrzucenia
        }
    }
}
