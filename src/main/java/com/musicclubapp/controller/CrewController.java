package com.musicclubapp.controller;

import com.musicclubapp.dto.CrewCardResponse;
import com.musicclubapp.dto.CrewDetailsResponse;
import com.musicclubapp.dto.CrewForm;
import com.musicclubapp.dto.CrewJoinRequest;
import com.musicclubapp.dto.CrewMessageRequest;
import com.musicclubapp.dto.CrewMessageResponse;
import com.musicclubapp.dto.MeetingRequest;
import com.musicclubapp.dto.MyCrewResponse;
import com.musicclubapp.service.CrewChatService;
import com.musicclubapp.service.CrewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Ekipy na koncert: lista pod wydarzeniem, zakladanie, dolaczanie, zarzadzanie i czat. */
@RestController
@Tag(name = "Ekipy", description = "Ekipy na koncert - kilka osob, ktore ida na to samo wydarzenie razem")
public class CrewController {

    private final CrewService crews;
    private final CrewChatService chat;

    public CrewController(CrewService crews, CrewChatService chat) {
        this.crews = crews;
        this.chat = chat;
    }

    @GetMapping("/api/events/{eventId}/crews")
    @Operation(summary = "Ekipy wydarzenia (moja pierwsza, potem z okolicy, z miejscami, ze znajomymi)")
    public ResponseEntity<List<CrewCardResponse>> ofEvent(@PathVariable Long eventId, Authentication auth) {
        return ResponseEntity.ok(crews.ofEvent(eventId, auth.getName()));
    }

    @PostMapping("/api/events/{eventId}/crews")
    @Operation(summary = "Zaklada ekipe (i zapisuje zakladajacego jako 'Biore udzial')")
    public ResponseEntity<CrewDetailsResponse> create(@PathVariable Long eventId, @Valid @RequestBody CrewForm form,
                                                      Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crews.create(eventId, auth.getName(), form));
    }

    @GetMapping("/api/crews/mine")
    @Operation(summary = "Moje ekipy na nadchodzace koncerty")
    public ResponseEntity<List<MyCrewResponse>> mine(Authentication auth) {
        return ResponseEntity.ok(crews.mine(auth.getName()));
    }

    @GetMapping("/api/crews/{id}")
    @Operation(summary = "Strona ekipy")
    public ResponseEntity<CrewDetailsResponse> details(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(crews.details(id, auth.getName()));
    }

    @PutMapping("/api/crews/{id}")
    @Operation(summary = "Zmiana opisu, limitu, naboru i miasta (zakladajacy)")
    public ResponseEntity<CrewDetailsResponse> update(@PathVariable Long id, @Valid @RequestBody CrewForm form,
                                                      Authentication auth) {
        return ResponseEntity.ok(crews.update(id, auth.getName(), form));
    }

    @PutMapping("/api/crews/{id}/closed")
    @Operation(summary = "Zamkniecie albo otwarcie naboru (zakladajacy)")
    public ResponseEntity<CrewDetailsResponse> closed(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                                      Authentication auth) {
        return ResponseEntity.ok(crews.setClosed(id, auth.getName(), Boolean.TRUE.equals(body.get("closed"))));
    }

    @PostMapping("/api/crews/{id}/join")
    @Operation(summary = "Dolacz (nabor otwarty) albo popros (za zgoda)")
    public ResponseEntity<CrewCardResponse> join(@PathVariable Long id, @Valid @RequestBody(required = false) CrewJoinRequest body,
                                                 Authentication auth) {
        return ResponseEntity.ok(crews.join(id, auth.getName(), body == null ? null : body.message()));
    }

    @DeleteMapping("/api/crews/{id}/request")
    @Operation(summary = "Cofniecie mojej prosby")
    public ResponseEntity<CrewCardResponse> cancelRequest(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(crews.cancelRequest(id, auth.getName()));
    }

    @PostMapping("/api/crews/{id}/requests/{requestId}/accept")
    public ResponseEntity<CrewDetailsResponse> accept(@PathVariable Long id, @PathVariable Long requestId,
                                                      Authentication auth) {
        return ResponseEntity.ok(crews.accept(id, requestId, auth.getName()));
    }

    @PostMapping("/api/crews/{id}/requests/{requestId}/decline")
    public ResponseEntity<CrewDetailsResponse> decline(@PathVariable Long id, @PathVariable Long requestId,
                                                       Authentication auth) {
        return ResponseEntity.ok(crews.decline(id, requestId, auth.getName()));
    }

    @DeleteMapping("/api/crews/{id}/members/me")
    @Operation(summary = "Odejscie z ekipy (zakladajacy przekazuje ja najdluzej obecnej osobie)")
    public ResponseEntity<Void> leave(@PathVariable Long id, Authentication auth) {
        crews.leave(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/crews/{id}/members/{username}")
    @Operation(summary = "Wyrzucenie czlonka (zakladajacy)")
    public ResponseEntity<CrewDetailsResponse> kick(@PathVariable Long id, @PathVariable String username,
                                                    Authentication auth) {
        return ResponseEntity.ok(crews.kick(id, auth.getName(), username));
    }

    /* --- czat --- */

    @GetMapping("/api/crews/{id}/chat")
    public ResponseEntity<List<CrewMessageResponse>> chat(@PathVariable Long id,
                                                          @RequestParam(required = false) Long after,
                                                          @RequestParam(required = false) Long before,
                                                          @RequestParam(defaultValue = "40") int limit,
                                                          Authentication auth) {
        return ResponseEntity.ok(chat.list(id, auth.getName(), after, before, limit));
    }

    @PostMapping("/api/crews/{id}/chat")
    public ResponseEntity<CrewMessageResponse> send(@PathVariable Long id, @Valid @RequestBody CrewMessageRequest body,
                                                    Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.send(id, auth.getName(), body.content()));
    }

    @PostMapping("/api/crews/{id}/chat/meeting")
    @Operation(summary = "Spotkanie na czacie ekipy (miejsce zbiorki)")
    public ResponseEntity<CrewMessageResponse> sendMeeting(@PathVariable Long id, @Valid @RequestBody MeetingRequest body,
                                                           Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.sendMeeting(id, auth.getName(), body));
    }

    @DeleteMapping("/api/crews/{id}/chat/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id, @PathVariable Long messageId, Authentication auth) {
        chat.delete(id, messageId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/crews/{id}/chat/changes")
    public ResponseEntity<CrewChatService.CrewChatChanges> changes(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            Authentication auth) {
        return ResponseEntity.ok(chat.changes(id, auth.getName(), since));
    }

    @PostMapping("/api/crews/{id}/chat/read")
    public ResponseEntity<Void> read(@PathVariable Long id, @RequestBody Map<String, Long> body, Authentication auth) {
        chat.markRead(id, auth.getName(), body.getOrDefault("upTo", 0L));
        return ResponseEntity.noContent().build();
    }
}
