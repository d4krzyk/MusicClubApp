package com.musicclubapp.service;

import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Laczy nasza baze danych ze Spring Security - to jest KROK 4 z planu.
 *
 * <p>Spring Security nie wie nic o naszej encji {@code User}. Zna tylko
 * interfejs {@link UserDetails}. Ta klasa jest tlumaczem: dostaje login,
 * wyciaga uzytkownika z bazy i przepisuje go na obiekt, ktory Security
 * rozumie.</p>
 *
 * <p>Wystarczy, ze taki bean istnieje w kontekscie - Spring Boot sam go
 * podepnie do mechanizmu logowania. Nie trzeba nic rejestrowac recznie.</p>
 *
 * <p><b>Dlaczego encja {@code User} nie implementuje {@code UserDetails}?</b>
 * Tak tez sie da i wiele tutoriali tak robi, ale wtedy encja bazodanowa miesza
 * sie z klasa frameworka bezpieczenstwa - encja dostaje metody
 * {@code isAccountNonExpired()} i podobne, ktore nie maja nic wspolnego
 * z baza. Rozdzielenie tego jest czystsze i latwiejsze do wytlumaczenia.</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Wywolywane przez Spring Security przy kazdej probie logowania.
     *
     * <p>Nie porownujemy tu hasel! Nasze zadanie konczy sie na zwroceniu
     * uzytkownika razem z hashem hasla - porownanie hashy robi za nas
     * {@code DaoAuthenticationProvider} przy uzyciu {@code PasswordEncoder}.</p>
     *
     * @throws UsernameNotFoundException gdy nie ma takiego uzytkownika
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "Nie znaleziono uzytkownika o loginie: " + username));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority(user.getRole().getAuthority())))
            .disabled(!user.isEnabled())
            .build();
    }
}
