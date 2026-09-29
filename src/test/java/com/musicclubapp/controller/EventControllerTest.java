package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.dto.EventView;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.dto.ParticipationResponse;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.EventParticipationService;
import com.musicclubapp.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dostep do zakladki Wydarzenia i przekazywanie parametrow. */
@WebMvcTest(EventController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("EventController - dostep i parametry")
class EventControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private EventService eventService;
    @MockBean private EventParticipationService participationService;
    @MockBean private com.musicclubapp.service.NetworkService network;
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
    @DisplayName("niezalogowany dostaje 401")
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("lista: rozmiar strony obciety do 50, miasto i fraza przekazane dalej")
    void listPassesFilters() throws Exception {
        given(eventService.list(any(), any(), any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/events").param("city", "krakow").param("q", "jazz").param("size", "5000"))
            .andExpect(status().isOk());

        verify(eventService).list(eq(EventView.UPCOMING), eq("krakow"), eq("jazz"), eq(PageRequest.of(0, 50)), eq("anna"));
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("nieistniejace wydarzenie to 404")
    void missingEventIs404() throws Exception {
        given(eventService.details(42L, "anna")).willThrow(new NoSuchElementFoundException("event", 42L));

        mockMvc.perform(get("/api/events/42")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zwykly uzytkownik nie dostaje szczegolow importu")
    void infoForUser() throws Exception {
        given(eventService.info(false, "anna")).willReturn(new EventsInfoResponse(true, "PL", List.of("PL"), false, List.of(), false, 0, false, null));

        mockMvc.perform(get("/api/events/info")).andExpect(status().isOk());

        verify(eventService).info(false, "anna");
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zapis bez tokenu CSRF jest odrzucany - to zmiana stanu")
    void participationNeedsCsrf() throws Exception {
        mockMvc.perform(put("/api/events/7/participation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"GOING\",\"hidden\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zapis: stan i ukrycie trafiaja do serwisu razem z zalogowanym")
    void participate() throws Exception {
        given(participationService.participate(7L, "anna", ParticipationStatus.GOING, true))
            .willReturn(new ParticipationResponse(ParticipationStatus.GOING, true, 1, 0, 1));

        mockMvc.perform(put("/api/events/7/participation").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"GOING\",\"hidden\":true}"))
            .andExpect(status().isOk());

        verify(participationService).participate(7L, "anna", ParticipationStatus.GOING, true);
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zapis bez stanu to blad walidacji (422, jak w calej aplikacji), a nie cicha rezygnacja")
    void participationNeedsStatus() throws Exception {
        mockMvc.perform(put("/api/events/7/participation").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("rezygnacja i lista uczestnikow")
    void cancelAndAttendees() throws Exception {
        given(participationService.cancel(7L, "anna"))
            .willReturn(new ParticipationResponse(null, false, 0, 0, 0));
        given(participationService.attendees(eq(7L), eq("anna"), any())).willReturn(Page.empty());

        mockMvc.perform(delete("/api/events/7/participation").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/7/attendees")).andExpect(status().isOk());

        verify(participationService).cancel(7L, "anna");
    }

    @Test
    @WithMockUser(username = "szef", roles = "ADMIN")
    @DisplayName("administrator dostaje stan importu")
    void infoForAdmin() throws Exception {
        given(eventService.info(true, "szef")).willReturn(new EventsInfoResponse(true, "PL", List.of("PL"), false, List.of(), false, 0, false, null));

        mockMvc.perform(get("/api/events/info")).andExpect(status().isOk());

        verify(eventService).info(true, "szef");
    }

    @Test
    @WithMockUser(username = "anna", roles = "USER")
    @DisplayName("zmiana kraju idzie do serwisu z zalogowanym; bez kraju - blad walidacji")
    void changeCountry() throws Exception {
        given(eventService.changeCountry("anna", "DE", false))
            .willReturn(new EventsInfoResponse(true, "DE", List.of("PL", "DE"), true, List.of(), false, 0, false, null));

        mockMvc.perform(put("/api/events/country").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"country\":\"DE\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/events/country").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"country\":\"\"}"))
            .andExpect(status().isUnprocessableEntity());

        verify(eventService).changeCountry("anna", "DE", false);
    }
}
