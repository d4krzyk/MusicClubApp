package com.musicclubapp.controller;

import com.musicclubapp.dto.PushSettingsResponse;
import com.musicclubapp.dto.PushSubscribeRequest;
import com.musicclubapp.dto.PushUnsubscribeRequest;
import com.musicclubapp.dto.ReminderSettingsRequest;
import com.musicclubapp.service.PushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Powiadomienia na telefon i przypomnienia o wydarzeniach. */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Powiadomienia push", description = "Urzadzenia i przypomnienia")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    @GetMapping
    @Operation(summary = "Czy push dziala, klucz serwera, przypomnienia i liczba urzadzen")
    public ResponseEntity<PushSettingsResponse> settings(Authentication authentication) {
        return ResponseEntity.ok(push.settings(authentication.getName()));
    }

    @PutMapping("/reminders")
    @Operation(summary = "Wlacza albo wylacza przypomnienia o wydarzeniach")
    public ResponseEntity<PushSettingsResponse> reminders(@RequestBody ReminderSettingsRequest payload,
                                                          Authentication authentication) {
        return ResponseEntity.ok(push.updateReminders(authentication.getName(), payload.eventReminders()));
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Zapisuje to urzadzenie do powiadomien push")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscribeRequest payload,
                                          Authentication authentication) {
        push.subscribe(authentication.getName(), payload);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    @Operation(summary = "Wypisuje to urzadzenie")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest payload,
                                            Authentication authentication) {
        push.unsubscribe(authentication.getName(), payload.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @Operation(summary = "Wysyla probne powiadomienie na moje urzadzenia")
    public ResponseEntity<Void> test(Authentication authentication) {
        push.sendTest(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
