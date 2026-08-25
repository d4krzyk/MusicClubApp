package com.musicclubapp.service;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testy jednostkowe edycji wlasnego profilu - wymaganie nr 13.
 *
 * <p>Osobna klasa od {@code UserServiceTest}, bo dotyczy innego obszaru
 * (ustawienia konta, nie rejestracja). Wyklad 5, slajd 8: jedna klasa testow
 * powinna sprawdzac jedna rzecz.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - edycja wlasnego profilu i zmiana hasla")
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User anna() {
        return new User("anna", "anna@example.com", "$2a$10$stary");
    }

    @Test
    @DisplayName("zapis bez zmiany loginu NIE zglasza 'login zajety'")
    void zapisBezZmianyLoginuDziala() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "anna", "nowy@example.com", false, LocalDateTime.now()));

        userService.updateProfile("anna", new UpdateProfileRequest("anna", "nowy@example.com"));

        /*
         * Kluczowe: przy niezmienionym loginie w ogole nie pytamy bazy
         * o jego zajetosc. Gdybysmy pytali, dostalibysmy "true" (bo login
         * nalezy do tego samego uzytkownika) i zapis by sie wywalil.
         */
        verify(userRepository, never()).existsByUsername("anna");
    }

    @Test
    @DisplayName("zmiana loginu na zajety przez kogos innego konczy sie wyjatkiem")
    void zajetyLoginInnegoUzytkownikaRzucaWyjatek() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(userRepository.existsByUsername("bartek")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("bartek", "anna@example.com")))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("zmiana e-maila na zajety przez kogos innego konczy sie wyjatkiem")
    void zajetyEmailInnegoUzytkownikaRzucaWyjatek() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(userRepository.existsByEmail("zajety@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("anna", "zajety@example.com")))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("poprawna zmiana loginu i e-maila zapisuje nowe wartosci")
    void poprawnaZmianaZapisujeDane() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(userRepository.existsByUsername("ania")).willReturn(false);
        given(userRepository.existsByEmail("ania@example.com")).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "ania", "ania@example.com", false, LocalDateTime.now()));

        userService.updateProfile("anna", new UpdateProfileRequest("ania", "ania@example.com"));

        ArgumentCaptor<User> zapisany = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getUsername()).isEqualTo("ania");
        assertThat(zapisany.getValue().getEmail()).isEqualTo("ania@example.com");
    }

    @Test
    @DisplayName("zle obecne haslo NIE pozwala zmienic hasla")
    void zleObecneHasloRzucaWyjatek() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(passwordEncoder.matches("zle", "$2a$10$stary")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(
            "anna", new ChangePasswordRequest("zle", "noweHaslo123", "noweHaslo123")))
            .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("poprawne obecne haslo zapisuje NOWY hash, nie jawne haslo")
    void poprawnaZmianaHaslaZapisujeHash() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(passwordEncoder.matches("tajneHaslo1", "$2a$10$stary")).willReturn(true);
        given(passwordEncoder.encode("noweHaslo123")).willReturn("$2a$10$nowy");
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        userService.changePassword(
            "anna", new ChangePasswordRequest("tajneHaslo1", "noweHaslo123", "noweHaslo123"));

        ArgumentCaptor<User> zapisany = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getPasswordHash()).isEqualTo("$2a$10$nowy");
        assertThat(zapisany.getValue().getPasswordHash()).isNotEqualTo("noweHaslo123");
    }
}
