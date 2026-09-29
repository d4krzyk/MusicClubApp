package com.musicclubapp.controller;

import com.musicclubapp.dto.PublicInfoResponse;
import com.musicclubapp.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Co frontend musi wiedziec o serwerze, zanim ktokolwiek sie zaloguje. */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Informacje o serwerze")
public class PublicInfoController {

    private final PasswordResetService passwordReset;

    public PublicInfoController(PasswordResetService passwordReset) {
        this.passwordReset = passwordReset;
    }

    /** Np. czy pokazywac "Nie pamietasz hasla?" - bez poczty link nie mialby jak dojsc. */
    @GetMapping("/info")
    @Operation(summary = "Funkcje serwera widoczne przed zalogowaniem")
    public ResponseEntity<PublicInfoResponse> info() {
        return ResponseEntity.ok(new PublicInfoResponse(passwordReset.enabled()));
    }
}
