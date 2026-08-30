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
 *
 * <p><b>Ta klasa pilnuje tez samego ISTNIENIA koncowek panelu.</b> Adres,
 * ktory zniknal, wyglada w kodzie dokladnie tak samo jak adres, ktorego nigdy
 * nie bylo - kompilator milczy, testy serwisow przechodza, a panel dostaje
 * 405. Dopoki jakis test nie zapuka pod konkretny adres, nikt tego nie
 * zauwazy.</p>
 */
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

    /**
     * Czy koncowka w ogole istnieje - i dlaczego to trzeba sprawdzac.
     *
     * <p><b>Ten test powstal po prawdziwym bledzie.</b> Przy scalaniu dwoch
     * osobnych zakazow w jeden adres {@code /{id}/bans/{kind}} zniknelo przy
     * okazji {@code DELETE /{id}}, ktore lezalo <b>pomiedzy</b> nimi w pliku.
     * Kod dalej sie kompilowal, wszystkie 351 testow przechodzilo, a panel
     * administratora dostawal 405 przy probie usuniecia konta - bo testy
     * sprawdzaly, co robi {@code UserModerationService}, ale nikt nie
     * sprawdzal, czy prowadzi do niego jakikolwiek adres.</p>
     *
     * <p>Brakujace mapowanie to blad, ktorego nie widac ani w kompilacji, ani
     * w testach logiki. Widac go dopiero w przegladarce - albo tutaj.</p>
     */
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
