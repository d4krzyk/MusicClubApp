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

/**
 * Odnotowuje aktywnosc zalogowanego uzytkownika przy kazdym zapytaniu do API.
 *
 * <p><b>Dlaczego przechwytywacz (interceptor), a nie filtr.</b> Filtr dziala
 * na kazdym zapytaniu, jakie trafia do serwera - takze na pobieraniu zdjec
 * spod {@code /uploads}. Te pobiera przegladarka sama, wstawiajac obrazki
 * w tablice, i jest ich kilkadziesiat na jedno wejscie na strone. To nie jest
 * aktywnosc czlowieka, tylko doladowywanie obrazkow. Przechwytywacz MVC
 * wpina sie dopiero przy zapytaniach obslugiwanych przez kontrolery, i o to
 * wlasnie chodzi.</p>
 *
 * <p><b>Dlaczego {@link ObjectProvider}, a nie zwykle wstrzykniecie.</b>
 * Ta klasa implementuje {@link WebMvcConfigurer}, a testy pisane
 * z {@code @WebMvcTest} wciagaja automatycznie wszystkie takie klasy - ta sama
 * pulapka, ktora opisuje {@code UploadsWebConfig}. Gdyby wymagala tu gotowego
 * {@code PresenceService}, kazdy test kontrolera musialby go podstawiac,
 * inaczej caly kontekst nie wstaje. {@code ObjectProvider} siega po bean
 * dopiero w chwili uzycia, a gdy go nie ma - przechwytywacz po prostu nic
 * nie robi.</p>
 */
@Configuration
public class PresenceWebConfig implements WebMvcConfigurer {

    /**
     * Naglowek z nazwa konta, ktore serwer widzi jako zalogowane.
     *
     * <p>Musi byc wymieniony w {@code setExposedHeaders} w konfiguracji CORS,
     * inaczej przegladarka go ukryje przed JavaScriptem - bez sladu w konsoli.</p>
     */
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

    /**
     * Zapisuje "byl tu przed chwila" i przepuszcza zapytanie dalej.
     *
     * <p>Dziala w {@code preHandle}, czyli PRZED wlasciwa obsluga. Gdyby
     * dzialal po niej, kazdy blad w kontrolerze gubilby przy okazji informacje
     * o aktywnosci - a to, ze ktos wywolal cos, co sie nie udalo, i tak znaczy,
     * ze byl obecny.</p>
     */
    private record PresenceInterceptor(ObjectProvider<PresenceService> presence)
        implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) {

            Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

            /*
             * isAuthenticated() nie wystarczy: niezalogowany uzytkownik ma
             * w kontekscie token anonimowy, ktory tez odpowiada "tak", a jego
             * nazwa to "anonymousUser". Bez tego sprawdzenia probowalibysmy
             * zapisywac aktywnosc konta, ktore nie istnieje.
             */
            if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {

                PresenceService service = presence.getIfAvailable();
                if (service != null) {
                    service.touch(authentication.getName());
                }

                /*
                 * Kto JEST teraz zalogowany - wedlug serwera.
                 *
                 * Sluzy do wykrycia sytuacji, ktora na jednym komputerze
                 * zdarza sie bardzo latwo: w drugiej karcie ktos loguje sie
                 * na inne konto. Ciasteczko sesji jest wspolne dla calej
                 * przegladarki, wiec od tej chwili STARA karta - nadal
                 * wygladajaca jak konto A - wysyla zapytania jako konto B
                 * i dostaje jego dane. Objawia sie to tym, ze "na starym
                 * uzytkowniku widac znajomych nowego".
                 *
                 * Serwer nie ma jak temu zapobiec (jedna przegladarka to
                 * jedno ciasteczko, czyli jedna tozsamosc), ale moze o tym
                 * UPRZEDZIC. Dopisujemy wiec do kazdej odpowiedzi nazwe
                 * zalogowanego konta, a przegladarka porownuje ja z tym,
                 * kogo sama u siebie wyswietla - szczegoly w kliencie HTTP
                 * po stronie Reacta.
                 */
                response.setHeader(CURRENT_USER_HEADER, authentication.getName());
            }

            return true;   // aktywnosc to notatka na boku, nigdy powod do odrzucenia
        }
    }
}
