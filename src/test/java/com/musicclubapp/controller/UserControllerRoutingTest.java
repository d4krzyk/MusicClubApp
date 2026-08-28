package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.entity.BlockedIp;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.NetworkService;
import com.musicclubapp.service.UserModerationService;
import com.musicclubapp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kolizja adresow w panelu administratora.
 *
 * <p><b>Dokladnie ta sama pulapka co przy {@code /api/posts/reactions}</b>,
 * i dlatego ten test w ogole istnieje. Doszedl adres
 * {@code GET /api/users/blocked-ips}, a obok stoi juz
 * {@code GET /api/users/{id}}, gdzie {@code id} jest liczba. Oba wzorce maja
 * dwa czlony i oba pasuja do tego samego adresu.</p>
 *
 * <p>Spring wybiera ten z doslownym czlonem - ale gdyby kiedys ktos przestawil
 * adresy albo zmienil typ parametru, objawiloby sie to bledem 400 ("nie umiem
 * zamienic 'blocked-ips' na liczbe") przy wchodzeniu na liste blokad. Zaden
 * test logiki tego nie zlapie, bo problem siedzi wylacznie w mapowaniu.</p>
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("UserController - kolizja adresow panelu")
class UserControllerRoutingTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;
    @MockBean private UserModerationService moderationService;
    @MockBean private NetworkService network;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("/api/users/blocked-ips trafia do listy blokad, a nie do konta o numerze 'blocked-ips'")
    void blockedIpsPathWinsOverTheIdPattern() throws Exception {
        given(network.blockedAddresses()).willReturn(List.<BlockedIp>of());

        mockMvc.perform(get("/api/users/blocked-ips"))
            .andExpect(status().isOk());

        verify(network).blockedAddresses();
        // Kluczowe: NIE poszlo do obslugi pojedynczego konta
        verify(userService, never()).getByIdForAdmin(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("zwykly uzytkownik nie wejdzie na liste blokad")
    void regularUserCannotSeeBlockedIps() throws Exception {
        mockMvc.perform(get("/api/users/blocked-ips"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("adres z liczba dalej prowadzi do konta")
    void numericPathStillReachesTheAccount() throws Exception {
        given(userService.getByIdForAdmin(7L)).willReturn(null);

        mockMvc.perform(get("/api/users/7"))
            .andExpect(status().isOk());

        verify(userService).getByIdForAdmin(7L);
    }
}
