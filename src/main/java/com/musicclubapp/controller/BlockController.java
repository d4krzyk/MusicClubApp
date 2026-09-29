package com.musicclubapp.controller;

import com.musicclubapp.dto.BlockedUserResponse;
import com.musicclubapp.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Blokowanie innych uzytkownikow - kazdy decyduje za siebie. */
@RestController
@RequestMapping("/api/blocks")
@Tag(name = "Blokady", description = "Blokowanie uzytkownikow")
public class BlockController {

    private final BlockService blocks;

    public BlockController(BlockService blocks) {
        this.blocks = blocks;
    }

    @GetMapping
    @Operation(summary = "Moja lista zablokowanych")
    public ResponseEntity<List<BlockedUserResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(blocks.blockedBy(authentication.getName()));
    }

    @PutMapping("/{username}")
    @Operation(summary = "Blokuje uzytkownika: zrywa znajomosc, ukrywa posty i profil w obie strony")
    public ResponseEntity<Void> block(@PathVariable String username, Authentication authentication) {
        blocks.block(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Odblokowuje uzytkownika (znajomosc nie wraca sama)")
    public ResponseEntity<Void> unblock(@PathVariable String username, Authentication authentication) {
        blocks.unblock(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }
}
