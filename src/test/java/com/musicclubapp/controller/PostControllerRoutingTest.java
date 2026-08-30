package com.musicclubapp.controller;

import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.PostService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kierowanie zapytan w PostController. */
@WebMvcTest(PostController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("PostController - adresy tablicy i licznikow reakcji")
class PostControllerRoutingTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PostService postService;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("/api/posts/reactions trafia do licznikow, a nie do posta o numerze 'reactions'")
    void reactionsPathWinsOverTheIdPattern() throws Exception {
        given(postService.reactionSummaries(any(), anyString()))
            .willReturn(Map.of(7L, ReactionSummary.empty()));

        mockMvc.perform(get("/api/posts/reactions").param("ids", "7,9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.7").exists());

        verify(postService).reactionSummaries(List.of(7L, 9L), "anna");
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("bez parametru scope tablica jest pelna (ALL)")
    void defaultScopeIsAll() throws Exception {
        given(postService.feed(anyString(), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isOk());

        verify(postService).feed(eq("anna"), eq(FeedScope.ALL), any());
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("scope=FRIENDS zaweza tablice do kregu znajomych")
    void friendsScopeIsPassedThrough() throws Exception {
        Page<com.musicclubapp.dto.PostResponse> empty = new PageImpl<>(List.of());
        given(postService.feed(anyString(), any(), any())).willReturn(empty);

        mockMvc.perform(get("/api/posts").param("scope", "FRIENDS"))
            .andExpect(status().isOk());

        verify(postService).feed(eq("anna"), eq(FeedScope.FRIENDS), any());
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("parametr author nadal prowadzi do postow jednego autora")
    void authorParameterStillWorks() throws Exception {
        given(postService.byAuthor(anyString(), anyString(), any()))
            .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/posts").param("author", "bartek"))
            .andExpect(status().isOk());

        verify(postService).byAuthor(eq("bartek"), eq("anna"), any());
    }
}
