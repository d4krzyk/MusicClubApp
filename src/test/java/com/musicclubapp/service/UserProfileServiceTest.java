package com.musicclubapp.service;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.InvalidCurrentPasswordException;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Testy jednostkowe edycji wlasnego profilu - wymaganie nr 13. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - edycja wlasnego profilu i zmiana hasla")
class UserProfileServiceTest {

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

    /** Linki i powiadomienia o koncie - poczta w tych testach wylaczona. */
    @Mock
    private AccountLinks accountLinks;

    @InjectMocks
    private UserService userService;

    private User anna() {
        return new User("anna", "anna@example.com", "$2a$10$stary");
    }

    @Test
    @DisplayName("zapis bez zmiany loginu NIE zglasza 'login zajety'")
    void saveWithoutUsernameChangeWorks() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "anna", "nowy@example.com", false, null, LocalDateTime.now(), true, null, false, false));

        given(passwordEncoder.matches("haslo", "$2a$10$stary")).willReturn(true);
        userService.updateProfile("anna", new UpdateProfileRequest("anna", "nowy@example.com", "haslo"));

        /* Kluczowe: przy niezmienionym loginie w ogole nie pytamy bazy o jego zajetosc. */
        verify(userRepository, never()).existsByUsername("anna");
    }

    @Test
    @DisplayName("zmiana loginu na zajety przez kogos innego konczy sie wyjatkiem")
    void usernameTakenByAnotherUserThrows() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(userRepository.existsByUsername("bartek")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("bartek", "anna@example.com", null)))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("zmiana e-maila na zajety przez kogos innego konczy sie wyjatkiem")
    void emailTakenByAnotherUserThrows() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(userRepository.existsByEmailIgnoreCase("zajety@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("anna", "zajety@example.com", "haslo")))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("poprawna zmiana loginu i e-maila zapisuje nowe wartosci")
    void validChangeIsSaved() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(userRepository.existsByUsername("ania")).willReturn(false);
        given(userRepository.existsByEmailIgnoreCase("ania@example.com")).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toResponse(any(User.class))).willReturn(
            new UserResponse(1L, "ania", "ania@example.com", false, null, LocalDateTime.now(), true, null, false, false));

        given(passwordEncoder.matches("haslo", "$2a$10$stary")).willReturn(true);
        userService.updateProfile("anna", new UpdateProfileRequest("ania", "ania@example.com", "haslo"));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());
        assertThat(stored.getValue().getUsername()).isEqualTo("ania");
        // Nowy adres idzie przez potwierdzanie: bez poczty zmienia sie od razu, z poczta czeka na link
        verify(emailVerification).requestChange(stored.getValue(), "ania@example.com");
    }

    @Test
    @DisplayName("zmiana adresu bez hasla albo ze zlym haslem - odmowa, nic nie zapisane")
    void emailChangeNeedsPassword() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(passwordEncoder.matches("zle", "$2a$10$stary")).willReturn(false);

        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("anna", "nowy@example.com", null)))
            .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThatThrownBy(() -> userService.updateProfile(
            "anna", new UpdateProfileRequest("anna", "nowy@example.com", "zle")))
            .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(emailVerification, never()).requestChange(any(), any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("zle obecne haslo NIE pozwala zmienic hasla")
    void wrongCurrentPasswordThrows() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(passwordEncoder.matches("zle", "$2a$10$stary")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(
            "anna", new ChangePasswordRequest("zle", "noweHaslo123", "noweHaslo123")))
            .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("poprawne obecne haslo zapisuje NOWY hash, nie jawne haslo")
    void validPasswordChangeStoresHash() {
        User anna = anna();
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna));
        given(passwordEncoder.matches("tajneHaslo1", "$2a$10$stary")).willReturn(true);
        given(passwordEncoder.encode("noweHaslo123")).willReturn("$2a$10$nowy");
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        userService.changePassword(
            "anna", new ChangePasswordRequest("tajneHaslo1", "noweHaslo123", "noweHaslo123"));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());
        assertThat(stored.getValue().getPasswordHash()).isEqualTo("$2a$10$nowy");
        assertThat(stored.getValue().getPasswordHash()).isNotEqualTo("noweHaslo123");
    }
}
