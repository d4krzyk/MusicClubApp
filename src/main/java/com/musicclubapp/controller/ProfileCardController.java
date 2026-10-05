package com.musicclubapp.controller;

import com.musicclubapp.dto.CardVisibilityRequest;
import com.musicclubapp.dto.PhotoOrderRequest;
import com.musicclubapp.dto.ProfileCardResponse;
import com.musicclubapp.dto.UpdateProfileCardRequest;
import com.musicclubapp.service.ProfileCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Moja karta profilu: zdjecia, "o mnie", "szukam" i pytania muzyczne. */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Karta profilu", description = "Galeria zdjec, opis i pytania muzyczne - na profilu i w trybie Poznawaj")
public class ProfileCardController {

    private final ProfileCardService cards;

    public ProfileCardController(ProfileCardService cards) {
        this.cards = cards;
    }

    @GetMapping("/card")
    @Operation(summary = "Moja karta profilu")
    public ResponseEntity<ProfileCardResponse> mine(Authentication auth) {
        return ResponseEntity.ok(cards.mine(auth.getName()));
    }

    @PutMapping("/card")
    @Operation(summary = "Zapisuje opis, \"szukam\" i pytania muzyczne (w calosci)")
    public ResponseEntity<ProfileCardResponse> update(@Valid @RequestBody UpdateProfileCardRequest payload,
                                                      Authentication auth) {
        return ResponseEntity.ok(cards.update(auth.getName(), payload));
    }

    @PutMapping("/card/visibility")
    @Operation(summary = "Kto widzi moja karte na profilu: wszyscy, znajomi albo nikt (tylko tryb Poznawaj)")
    public ResponseEntity<ProfileCardResponse> visibility(@Valid @RequestBody CardVisibilityRequest payload,
                                                          Authentication auth) {
        return ResponseEntity.ok(cards.setVisibility(auth.getName(), payload.visibility()));
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Dodaje zdjecie na koniec galerii (najwyzej 6; JPG, PNG albo WebP)")
    public ResponseEntity<ProfileCardResponse> addPhoto(@RequestPart("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.ok(cards.addPhoto(auth.getName(), file));
    }

    @DeleteMapping("/photos/{id}")
    @Operation(summary = "Usuwa zdjecie z galerii")
    public ResponseEntity<ProfileCardResponse> removePhoto(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(cards.removePhoto(auth.getName(), id));
    }

    @PutMapping("/photos/order")
    @Operation(summary = "Nowa kolejnosc zdjec (wszystkie numery; pierwsze = okladka karty)")
    public ResponseEntity<ProfileCardResponse> reorder(@Valid @RequestBody PhotoOrderRequest payload,
                                                       Authentication auth) {
        return ResponseEntity.ok(cards.reorder(auth.getName(), payload.ids()));
    }
}
