package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dostep do zakladki Wydarzenia i przekazywanie parametrow. */
@WebMvcTest(EventController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("EventController - dostep i parametry")
class EventControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private EventService eventService;
    @MockBean private com.musicclubapp.service.NetworkService network;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;

    @Test
    @DisplayName("niezalogowany dostaje 401")
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("lista: rozmiar strony obciety do 50, miasto i fraza przekazane dalej")
    void listPassesFilters() throws Exception {
        given(eventService.list(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/events").param("city", "krakow").param("q", "jazz").param("size", "5000"))
            .andExpect(status().isOk());

        verify(eventService).list(eq("krakow"), eq("jazz"), eq(PageRequest.of(0, 50)));
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("nieistniejace wydarzenie to 404")
    void missingEventIs404() throws Exception {
        given(eventService.details(42L)).willThrow(new NoSuchElementFoundException("event", 42L));

        mockMvc.perform(get("/api/events/42")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zwykly uzytkownik nie dostaje szczegolow importu")
    void infoForUser() throws Exception {
        given(eventService.info(false)).willReturn(new EventsInfoResponse(true, List.of(), null));

        mockMvc.perform(get("/api/events/info")).andExpect(status().isOk());

        verify(eventService).info(false);
    }

    @Test
    @WithMockUser(username = "szef", roles = "ADMIN")
    @DisplayName("administrator dostaje stan importu")
    void infoForAdmin() throws Exception {
        given(eventService.info(true)).willReturn(new EventsInfoResponse(true, List.of(), null));

        mockMvc.perform(get("/api/events/info")).andExpect(status().isOk());

        verify(eventService).info(true);
    }
}
