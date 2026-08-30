package com.musicclubapp.controller;

import com.musicclubapp.dto.NotificationResponse;
import com.musicclubapp.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Powiadomienia zalogowanego uzytkownika. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Powiadomienia", description = "Co sie wydarzylo w sprawach uzytkownika")
public class NotificationController {

    /** Zabezpieczenie przed {@code ?size=1000000}. */
    private static final int MAX_SIZE = 50;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Powiadomienia od najnowszych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona powiadomien"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<NotificationResponse>> list(
            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile na stronie (max 50)")
            @RequestParam(defaultValue = "15") int size,

            Authentication authentication) {

        return ResponseEntity.ok(notificationService.forUser(
            authentication.getName(),
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    /** Sama liczba nieprzeczytanych - to ona wisi przy dzwonku. */
    @GetMapping("/unread-count")
    @Operation(summary = "Liczba nieprzeczytanych powiadomien")
    public ResponseEntity<Map<String, Long>> countUnread(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "count", notificationService.countUnread(authentication.getName())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Oznacza jedno powiadomienie jako przeczytane")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Oznaczone"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Oznacza wszystkie powiadomienia jako przeczytane")
    public ResponseEntity<Map<String, Integer>> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "marked", notificationService.markAllRead(authentication.getName())));
    }

    /** Kasuje jedno powiadomienie. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Usuwa jedno powiadomienie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usuniete"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        notificationService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
