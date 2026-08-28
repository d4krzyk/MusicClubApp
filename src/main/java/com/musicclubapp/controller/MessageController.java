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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Czat: rozmowy zalogowanego uzytkownika z jego znajomymi.
 *
 * <p><b>Dlaczego rozmowy siedza pod {@code /with/{login}}, a nie wprost pod
 * {@code /api/messages/{login}}.</b> Adresy {@code /conversations}
 * i {@code /unread-count} sa staleymi czlonami sciezki, a login jest wartoscia
 * zmienna - i te dwie rzeczy stoja na tym samym poziomie. Spring wybralby
 * wprawdzie czlon staly (jest bardziej szczegolowy), ale skutek bylby taki,
 * ze uzytkownik o loginie {@code conversations} traci mozliwosc rozmowy -
 * i to bez zadnego bledu, po prostu wchodzac na cudza liste. Dodatkowy
 * czlon {@code /with/} usuwa ten przypadek calkowicie, zamiast liczyc na to,
 * ze nikt takiego loginu nie zalozy.</p>
 *
 * <p><b>Kim jestem, bierzemy z sesji</b> - nigdy z adresu ani z tresci
 * zapytania. Nie ma tu wiec czego podmienic, zeby czytac cudza rozmowe.</p>
 */
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

    /**
     * Sama liczba nieprzeczytanych - to ona wisi przy ikonie czatu.
     *
     * <p>Osobny, lekki adres, dokladnie tak jak przy powiadomieniach:
     * pytamy o to przy kazdej zmianie strony, a pobieranie calej listy rozmow
     * tylko po to, zeby narysowac liczbe, byloby marnotrawstwem.</p>
     */
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

        /*
         * Bez sortowania z Pageable. Kolejnosc ustala samo zapytanie
         * (od najnowszej), a Spring Data doklejalby swoja na jego koncu -
         * co przy stronicowaniu potrafi zgubic albo powtorzyc wiadomosc.
         */
        return ResponseEntity.ok(messageService.conversation(
            authentication.getName(),
            username,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    /**
     * Co nowego w otwartej rozmowie: wiadomosci, dymek "pisze" i obecnosc.
     *
     * <p>Trzy odpowiedzi w jednym zapytaniu - po co, opisuje
     * {@link ConversationSyncResponse}.</p>
     *
     * @param after identyfikator ostatniej wiadomosci, ktora przegladarka juz
     *              ma. Puste znaczy "nie mam zadnej"
     */
    @GetMapping("/with/{username}/sync")
    @Operation(summary = "Nowe wiadomosci, sygnal pisania i obecnosc rozmowcy")
    public ResponseEntity<ConversationSyncResponse> sync(
            @PathVariable String username,
            @RequestParam(required = false) Long after,
            Authentication authentication) {

        return ResponseEntity.ok(
            messageService.sync(authentication.getName(), username, after));
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

    /**
     * "Wlasnie pisze" - sygnal wysylany przez przegladarke przy pisaniu.
     *
     * <p>Odpowiadamy 204 i niczym wiecej: to jest notatka o stanie, ktory
     * zyje pare sekund, a nie operacja tworzaca cokolwiek trwalego.
     * Przechowuje ja {@code TypingRegistry} - w pamieci, nie w bazie.</p>
     */
    @PostMapping("/with/{username}/typing")
    @Operation(summary = "Sygnal, ze wlasnie pisze do tej osoby")
    public ResponseEntity<Void> typing(@PathVariable String username,
                                       Authentication authentication) {
        messageService.typing(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }
}
