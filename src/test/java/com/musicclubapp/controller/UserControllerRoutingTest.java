package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.entity.BanKind;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kolizja adresow w panelu administratora. */
@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("UserController - adresy panelu: kolizje i istnienie")
class UserControllerRoutingTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;
    @MockBean private UserModerationService moderationService;
    @MockBean private NetworkService network;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;

    /**
     * Filtr znacznika bezpieczenstwa pyta o konto przy kazdym zapytaniu z sesja.
     * Atrapa bez tej odpowiedzi zwraca "konta nie ma" - i filtr wylogowuje.
     */
    @org.junit.jupiter.api.BeforeEach
    void kontoIstnieje() {
        org.mockito.BDDMockito.given(userRepository.securityStampOf(org.mockito.ArgumentMatchers.any()))
            .willReturn(java.util.Optional.of(""));
    }

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

    /** Czy koncowka w ogole istnieje - i dlaczego to trzeba sprawdzac. */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("DELETE /api/users/{id} istnieje i usuwa konto")
    void deleteEndpointExists() throws Exception {
        mockMvc.perform(delete("/api/users/7").with(csrf()))
            .andExpect(status().isNoContent());

        verify(moderationService).deleteUser("admin", 7L);
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("zwykly uzytkownik nie usunie cudzego konta")
    void regularUserCannotDeleteAccounts() throws Exception {
        mockMvc.perform(delete("/api/users/7").with(csrf()))
            .andExpect(status().isForbidden());

        verify(moderationService, never()).deleteUser(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PATCH /api/users/{id}/bans/{kind} istnieje dla obu rodzajow kary")
    void banEndpointExistsForBothKinds() throws Exception {
        for (String rodzaj : new String[] { "POSTING", "MESSAGING" }) {
            mockMvc.perform(patch("/api/users/7/bans/" + rodzaj)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"hours\":24,\"forever\":false}"))
                .andExpect(status().isOk());
        }

        verify(moderationService).setBan(eq("admin"), eq(7L), eq(BanKind.POSTING), any());
        verify(moderationService).setBan(eq("admin"), eq(7L), eq(BanKind.MESSAGING), any());
    }
}
