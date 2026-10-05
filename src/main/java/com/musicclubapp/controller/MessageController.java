package com.musicclubapp.controller;

import com.musicclubapp.dto.ConversationResponse;
import com.musicclubapp.dto.ConversationSyncResponse;
import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.dto.SendMessageRequest;
import com.musicclubapp.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Czat: rozmowy zalogowanego uzytkownika z jego znajomymi. */
@RestController
@RequestMapping("/api/messages")
@Tag(name = "Wiadomosci", description = "Czat ze znajomymi")
public class MessageController {

    /** Zabezpieczenie przed {@code ?size=1000000}. */
    private static final int MAX_SIZE = 50;

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversations")
    @Operation(summary = "Wszyscy znajomi z ostatnia wiadomoscia i licznikiem nieprzeczytanych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rozmow"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<List<ConversationResponse>> conversations(Authentication authentication) {
        return ResponseEntity.ok(messageService.conversations(authentication.getName()));
    }

    /** Sama liczba nieprzeczytanych - to ona wisi przy ikonie czatu. */
    @GetMapping("/unread-count")
    @Operation(summary = "Laczna liczba nieprzeczytanych wiadomosci")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "count", messageService.unreadCount(authentication.getName())));
    }

    @GetMapping("/with/{username}")
    @Operation(summary = "Historia rozmowy, od najnowszej wiadomosci")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona wiadomosci"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "To nie jest znajomy")
    })
    public ResponseEntity<Page<MessageResponse>> conversation(
            @PathVariable String username,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile na stronie (max 50)")
            @RequestParam(defaultValue = "25") int size,

            Authentication authentication) {

        /* Bez sortowania z Pageable. */
        return ResponseEntity.ok(messageService.conversation(
            authentication.getName(),
            username,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    /** Co nowego w otwartej rozmowie: wiadomosci, dymek "pisze" i obecnosc. */
    @GetMapping("/with/{username}/sync")
    @Operation(summary = "Nowe wiadomosci, sygnal pisania i obecnosc rozmowcy")
    public ResponseEntity<ConversationSyncResponse> sync(
            @PathVariable String username,
            @RequestParam(required = false) Long after,
            @Parameter(description = "Czas serwera z poprzedniej odpowiedzi - oddamy wiadomosci usuniete od tej chwili")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime changedSince,
            Authentication authentication) {

        return ResponseEntity.ok(
            messageService.sync(authentication.getName(), username, after, changedSince));
    }

    /** Usuwa wlasna wiadomosc u obu stron - zostaje slad "wiadomosc usunieta". */
    @DeleteMapping("/{id}")
    @Operation(summary = "Usuwa moja wiadomosc u obu stron (zostaje slad)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usunieta - wiadomosc ze znacznikiem deleted"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiej wiadomosci albo nie jest moja")
    })
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(messageService.delete(authentication.getName(), id));
    }

    @PostMapping("/with/{username}")
    @Operation(summary = "Wysyla wiadomosc do znajomego")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Wyslana"),
        @ApiResponse(responseCode = "400", description = "Pusta wiadomosc albo zly link"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409",
                     description = "To nie jest znajomy albo konto ma zakaz publikowania")
    })
    public ResponseEntity<MessageResponse> send(
            @PathVariable String username,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {

        MessageResponse sent = messageService.send(authentication.getName(), username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sent);
    }

    @PostMapping("/with/{username}/read")
    @Operation(summary = "Oznacza cala rozmowe jako przeczytana")
    public ResponseEntity<Map<String, Integer>> markRead(@PathVariable String username,
                                                         Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "marked", messageService.markRead(authentication.getName(), username)));
    }

    /** Usuwa rozmowe u zalogowanego; druga strona zachowuje swoja kopie. */
    @DeleteMapping("/with/{username}")
    @Operation(summary = "Usuwa rozmowe z tym uzytkownikiem - tylko u siebie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rozmowa usunieta u zalogowanego"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<Void> deleteConversation(@PathVariable String username,
                                                   Authentication authentication) {
        messageService.deleteConversation(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }

    /** "Wlasnie pisze" - sygnal wysylany przez przegladarke przy pisaniu. */
    @PostMapping("/with/{username}/typing")
    @Operation(summary = "Sygnal, ze wlasnie pisze do tej osoby")
    public ResponseEntity<Void> typing(@PathVariable String username,
                                       Authentication authentication) {
        messageService.typing(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }
}
