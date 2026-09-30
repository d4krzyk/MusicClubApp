package com.musicclubapp.controller;

import com.musicclubapp.dto.ClanColorRequest;
import com.musicclubapp.dto.ClanMessageRequest;
import com.musicclubapp.dto.ClanMessageResponse;
import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.dto.ClanRoleRequest;
import com.musicclubapp.dto.ClanUsernameRequest;
import com.musicclubapp.dto.CreateClanRequest;
import com.musicclubapp.dto.MyClanResponse;
import com.musicclubapp.dto.UpdateClanRequest;
import com.musicclubapp.service.ClanChatService;
import com.musicclubapp.service.ClanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Klany: zakladanie, zaproszenia, czlonkowie, kolor z glosowania i czat. */
@RestController
@RequestMapping("/api/clans")
@Tag(name = "Klany", description = "Klany, zaproszenia i czat klanu")
public class ClanController {

    private final ClanService clans;
    private final ClanChatService chat;

    public ClanController(ClanService clans, ClanChatService chat) {
        this.clans = clans;
        this.chat = chat;
    }

    @GetMapping("/mine")
    @Operation(summary = "Moj klan i zaproszenia, ktore na mnie czekaja")
    public ResponseEntity<MyClanResponse> mine(Authentication auth) {
        return ResponseEntity.ok(clans.mine(auth.getName()));
    }

    @PostMapping
    @Operation(summary = "Zaklada klan")
    public ResponseEntity<ClanResponse> create(@Valid @RequestBody CreateClanRequest payload, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clans.create(auth.getName(), payload));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Strona klanu")
    public ResponseEntity<ClanResponse> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clans.get(id, auth.getName()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmienia opis (zarzad klanu) albo nazwe i skrot (zalozyciel)")
    public ResponseEntity<ClanResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateClanRequest payload,
                                               Authentication auth) {
        return ResponseEntity.ok(clans.update(id, auth.getName(), payload));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Rozwiazuje klan (zalozyciel albo administrator aplikacji)")
    public ResponseEntity<Void> disband(@PathVariable Long id, Authentication auth) {
        clans.disband(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    /* --- obrazy --- */

    @PostMapping(value = "/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ustawia ikone klanu")
    public ResponseEntity<ClanResponse> setIcon(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                Authentication auth) {
        return ResponseEntity.ok(clans.setIcon(id, auth.getName(), file));
    }

    @DeleteMapping("/{id}/icon")
    public ResponseEntity<ClanResponse> removeIcon(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clans.removeIcon(id, auth.getName()));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ustawia zdjecie klanu")
    public ResponseEntity<ClanResponse> setPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                 Authentication auth) {
        return ResponseEntity.ok(clans.setPhoto(id, auth.getName(), file));
    }

    @DeleteMapping("/{id}/photo")
    public ResponseEntity<ClanResponse> removePhoto(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clans.removePhoto(id, auth.getName()));
    }

    /* --- zaproszenia --- */

    @PostMapping("/{id}/invitations")
    @Operation(summary = "Czlonek zaprasza osobe do klanu")
    public ResponseEntity<ClanResponse> invite(@PathVariable Long id, @Valid @RequestBody ClanUsernameRequest payload,
                                               Authentication auth) {
        return ResponseEntity.ok(clans.invite(id, auth.getName(), payload.username()));
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    @Operation(summary = "Cofa zaproszenie")
    public ResponseEntity<ClanResponse> cancelInvitation(@PathVariable Long id, @PathVariable Long invitationId,
                                                         Authentication auth) {
        return ResponseEntity.ok(clans.cancelInvitation(id, invitationId, auth.getName()));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "Przyjmuje zaproszenie - jedyna droga do klanu")
    public ResponseEntity<MyClanResponse> accept(@PathVariable Long invitationId, Authentication auth) {
        return ResponseEntity.ok(clans.accept(invitationId, auth.getName()));
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @Operation(summary = "Odrzuca zaproszenie")
    public ResponseEntity<MyClanResponse> decline(@PathVariable Long invitationId, Authentication auth) {
        return ResponseEntity.ok(clans.decline(invitationId, auth.getName()));
    }

    /* --- czlonkostwo --- */

    @DeleteMapping("/{id}/members/me")
    @Operation(summary = "Odchodzi z klanu")
    public ResponseEntity<MyClanResponse> leave(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clans.leave(id, auth.getName()));
    }

    @DeleteMapping("/{id}/members/{username}")
    @Operation(summary = "Wyrzuca czlonka (zarzad klanu)")
    public ResponseEntity<ClanResponse> kick(@PathVariable Long id, @PathVariable String username, Authentication auth) {
        return ResponseEntity.ok(clans.kick(id, auth.getName(), username));
    }

    @PutMapping("/{id}/members/{username}/role")
    @Operation(summary = "Awansuje na administratora klanu albo cofa awans (zalozyciel)")
    public ResponseEntity<ClanResponse> changeRole(@PathVariable Long id, @PathVariable String username,
                                                   @Valid @RequestBody ClanRoleRequest payload, Authentication auth) {
        return ResponseEntity.ok(clans.changeRole(id, auth.getName(), username, payload.role()));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Przekazuje klan innemu czlonkowi (zalozyciel)")
    public ResponseEntity<ClanResponse> transfer(@PathVariable Long id, @Valid @RequestBody ClanUsernameRequest payload,
                                                 Authentication auth) {
        return ResponseEntity.ok(clans.transfer(id, auth.getName(), payload.username()));
    }

    /* --- kolor --- */

    @PutMapping("/{id}/color")
    @Operation(summary = "Moj glos na kolor klanu")
    public ResponseEntity<ClanResponse> vote(@PathVariable Long id, @Valid @RequestBody ClanColorRequest payload,
                                             Authentication auth) {
        return ResponseEntity.ok(clans.vote(id, auth.getName(), payload.color()));
    }

    @DeleteMapping("/{id}/color")
    @Operation(summary = "Cofa moj glos na kolor")
    public ResponseEntity<ClanResponse> unvote(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clans.vote(id, auth.getName(), null));
    }

    /* --- czat --- */

    @GetMapping("/{id}/chat")
    @Operation(summary = "Wiadomosci czatu klanu: ostatnie, nowsze od 'after' albo starsze od 'before'")
    public ResponseEntity<List<ClanMessageResponse>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "" + ClanChatService.DOMYSLNIE) int limit,
            Authentication auth) {
        return ResponseEntity.ok(chat.list(id, auth.getName(), after, before, limit));
    }

    @PostMapping("/{id}/chat")
    @Operation(summary = "Pisze na czacie klanu")
    public ResponseEntity<ClanMessageResponse> send(@PathVariable Long id, @Valid @RequestBody ClanMessageRequest payload,
                                                    Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.send(id, auth.getName(), payload.content()));
    }

    @DeleteMapping("/{id}/chat/{messageId}")
    @Operation(summary = "Usuwa wiadomosc z czatu klanu")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id, @PathVariable Long messageId, Authentication auth) {
        chat.delete(id, messageId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
