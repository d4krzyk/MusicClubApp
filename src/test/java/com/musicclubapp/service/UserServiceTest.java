package com.musicclubapp.service;

import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Testy jednostkowe serwisu - wymaganie nr 13. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - logika rejestracji i wyszukiwania")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    /** Nowa zaleznosc serwisu - obsluga plikow (zdjecia profilowe). */
    @Mock
    private FileStorageService fileStorage;

    /** Potwierdzanie adresu - tu tylko sprawdzamy, ze serwis je wola. */
    @Mock
    private EmailVerificationService emailVerification;

    @InjectMocks
    private UserService userService;

    private RegisterRequest reportViolation() {
        return new RegisterRequest("anna", "anna@example.com", "tajneHaslo1", "tajneHaslo1");
    }

    @Test
    @DisplayName("rejestracja zapisuje uzytkownika z ZAHASHOWANYM haslem")
    void registrationHashesPassword() {
        given(userRepository.existsByUsername("anna")).willReturn(false);
        given(userRepository.existsByEmailIgnoreCase("anna@example.com")).willReturn(false);
        given(passwordEncoder.encode("tajneHaslo1")).willReturn("$2a$10$zahashowane");
        given(userRepository.save(any(User.class))).willAnswer(wywolanie -> wywolanie.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now(), true, null));

        userService.register(reportViolation());

        // Sprawdzamy, CO dokladnie poszlo do repozytorium
        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());

        assertThat(stored.getValue().getUsername()).isEqualTo("anna");
        assertThat(stored.getValue().getPasswordHash()).isEqualTo("$2a$10$zahashowane");
        // najwazniejsze: jawne haslo NIE trafia do bazy
        assertThat(stored.getValue().getPasswordHash()).isNotEqualTo("tajneHaslo1");
        // ...i zapisane konto idzie do potwierdzenia adresu
        verify(emailVerification).afterRegistration(stored.getValue());
    }

    @Test
    @DisplayName("nowy uzytkownik dostaje role USER")
    void newUserGetsUserRole() {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmailIgnoreCase(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hash");
        given(userRepository.save(any(User.class))).willAnswer(wywolanie -> wywolanie.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now(), true, null));

        userService.register(reportViolation());

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());
        assertThat(stored.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("zajety login przerywa rejestracje i nic nie zapisuje")
    void takenUsernameThrows() {
        given(userRepository.existsByUsername("anna")).willReturn(true);

        assertThatThrownBy(() -> userService.register(reportViolation()))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("anna");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("zajety e-mail przerywa rejestracje i nic nie zapisuje")
    void takenEmailThrows() {
        given(userRepository.existsByUsername("anna")).willReturn(false);
        given(userRepository.existsByEmailIgnoreCase("anna@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.register(reportViolation()))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("szukanie nieistniejacego uzytkownika rzuca wyjatek 'nie znaleziono'")
    void missingUserThrows() {
        given(userRepository.findByUsername("duch")).willReturn(Optional.empty());

        // wymaganie nr 11 - wyjatek przy braku elementu w bazie
        assertThatThrownBy(() -> userService.getByUsername("duch"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }

    @Test
    @DisplayName("istniejacy uzytkownik jest zwracany jako DTO, bez hasha hasla")
    void existingUserIsMappedToDto() {
        User user = new User("anna", "anna@example.com", "$2a$10$hash");
        UserResponse oczekiwany =
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now(), true, null);

        given(userRepository.findByUsername("anna")).willReturn(Optional.of(user));
        given(userMapper.toResponse(user)).willReturn(oczekiwany);

        UserResponse score = userService.getByUsername("anna");

        assertThat(score).isEqualTo(oczekiwany);
        assertThat(score.username()).isEqualTo("anna");
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie po usunietym koncie                                     */
    /* ------------------------------------------------------------------ */

    /** Kasowanie awatara przenioslo sie tutaj z modulu moderacji. */
    @Test
    @DisplayName("usuwanie konta zdejmuje z dysku jego awatar")
    void deletingAccountRemovesItsAvatar() {
        User user = new User("anna", "anna@example.com", "hash");
        user.setAvatarFileName("awatar.jpg");

        userService.deleteAvatarOf(user);

        verify(fileStorage).remove("awatar.jpg");
    }

    @Test
    @DisplayName("konto bez awatara nie kasuje zadnego pliku")
    void accountWithoutAvatarRemovesNothing() {
        /*
         * Skladnica plikow sama pilnuje, zeby nie ruszac niczego przy pustej nazwie - ale
         * sprawdzamy to stad, bo to TUTAJ decydujemy, ze wolamy ja bezwarunkowo, zamiast owijac
         * wywolanie w "jesli nie null".
         */
        User user = new User("anna", "anna@example.com", "hash");

        userService.deleteAvatarOf(user);

        verify(fileStorage).remove(null);
    }
}
