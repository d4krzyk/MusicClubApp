package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrola dostepu do listy uzytkownikow.
 *
 * <p>To najwazniejsze testy w tym kroku. Ukrycie przycisku w interfejsie
 * NIE jest zabezpieczeniem - kazdy moze wpisac adres recznie albo wyslac
 * zapytanie z konsoli. Te testy pilnuja, ze o dostepie decyduje backend.</p>
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("UserController - lista uzytkownikow tylko dla administratora")
class UserControllerAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("niezalogowany dostaje 401")
    void niezalogowanyDostaje401() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zwykly uzytkownik dostaje 403 - nie widzi listy innych kont")
    void zwyklyUzytkownikDostaje403() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zwykly uzytkownik nie podejrzy tez pojedynczego konta po ID")
    void zwyklyUzytkownikNieWidziKontaPoId() throws Exception {
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("administrator widzi liste")
    void administratorWidziListe() throws Exception {
        Page<com.musicclubapp.dto.UserResponse> pusta = new PageImpl<>(List.of());
        given(userService.search(anyString(), any())).willReturn(pusta);

        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk());
    }
}
