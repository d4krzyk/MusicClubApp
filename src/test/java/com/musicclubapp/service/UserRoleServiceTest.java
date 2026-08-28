package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.ChangeRoleRequest;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
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

/**
 * Testy zmiany rol przez administratora - wymaganie nr 13.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - zmiana roli przez administratora")
class UserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    /** Nowa zaleznosc serwisu - obsluga plikow (zdjecia profilowe). */
    @Mock
    private FileStorageService fileStorage;

    @Mock
    private com.musicclubapp.repository.ReportRepository reportRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("nadanie roli ADMIN innemu uzytkownikowi zapisuje zmiane")
    void grantingAdminRole() {
        User anna = new User("anna", "anna@example.com", "hash");
        given(userRepository.findById(2L)).willReturn(Optional.of(anna));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toAdminResponse(any(User.class), org.mockito.ArgumentMatchers.anyLong())).willReturn(
            new AdminUserResponse(2L, "anna", "anna@example.com", Role.ADMIN,
                LocalDateTime.now(), null, null, 0));

        userService.changeRole("admin", 2L, new ChangeRoleRequest(Role.ADMIN));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());
        assertThat(stored.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("odebranie roli innemu administratorowi jest dozwolone")
    void revokingRoleFromAnotherAdmin() {
        User other = new User("drugiadmin", "drugi@example.com", "hash");
        other.setRole(Role.ADMIN);
        given(userRepository.findById(3L)).willReturn(Optional.of(other));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        given(userMapper.toAdminResponse(any(User.class), org.mockito.ArgumentMatchers.anyLong())).willReturn(
            new AdminUserResponse(3L, "drugiadmin", "drugi@example.com", Role.USER,
                LocalDateTime.now(), null, null, 0));

        userService.changeRole("admin", 3L, new ChangeRoleRequest(Role.USER));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());
        assertThat(stored.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("administrator NIE moze zmienic wlasnej roli - inaczej zamknalby sobie panel")
    void changingOwnRoleIsBlocked() {
        User admin = new User("admin", "admin@musicclub.local", "hash");
        admin.setRole(Role.ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.changeRole(
            "admin", 1L, new ChangeRoleRequest(Role.USER)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("zmiana roli nieistniejacego konta konczy sie wyjatkiem 'nie znaleziono'")
    void unknownAccountThrows() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(
            "admin", 999L, new ChangeRoleRequest(Role.ADMIN)))
            .isInstanceOf(NoSuchElementFoundException.class);
    }
}
