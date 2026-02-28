package com.musicclubapp.controller;

import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Get user by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @Operation(summary = "Get users with pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of users")
    })
    @GetMapping
    public ResponseEntity<Page<User>> getAll(Pageable pageable) {
        return ResponseEntity.ok(userRepository.findAll(pageable));
    }

    @Operation(summary = "Find users with similar artists")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matched users page")
    })
    @GetMapping("/{id}/similar")
    public ResponseEntity<Page<User>> getSimilar(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(userRepository.findUsersWithSimilarArtists(id, pageable));
    }
}

