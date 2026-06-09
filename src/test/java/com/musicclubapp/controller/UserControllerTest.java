package com.musicclubapp.controller;

import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.security.JwtAuthFilter;
import com.musicclubapp.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void getById_returnsUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("secret123");

        when(userService.getById(anyLong())).thenReturn(user);

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getAll_returnsPage() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setPassword("secret123");

        when(userRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/users?page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(2))
            .andExpect(jsonPath("$.content[0].username").value("bob"))
            .andExpect(jsonPath("$.content[0].email").value("bob@example.com"));
    }
}
