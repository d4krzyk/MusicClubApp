package com.musicclubapp.controller;

import com.musicclubapp.dto.CityHint;
import com.musicclubapp.dto.UpdateLocationRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Miasto z profilu i podpowiedzi do pola "Miasto". */
@RestController
@RequestMapping("/api")
@Tag(name = "Lokalizacja", description = "Miasto w profilu - po nim aplikacja stawia wyzej ludzi, koncerty i klany z okolicy")
public class LocationController {

    private final LocationService location;

    public LocationController(LocationService location) {
        this.location = location;
    }

    @PutMapping("/profile/location")
    @Operation(summary = "Ustawia miasto w profilu (pusty tekst je usuwa)")
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateLocationRequest payload,
                                               Authentication authentication) {
        return ResponseEntity.ok(location.update(authentication.getName(), payload.city()));
    }

    @GetMapping("/cities")
    @Operation(summary = "Podpowiedzi miast do pola \"Miasto\"")
    public ResponseEntity<List<CityHint>> hints(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(location.hints(q.length() > 60 ? q.substring(0, 60) : q));
    }
}
