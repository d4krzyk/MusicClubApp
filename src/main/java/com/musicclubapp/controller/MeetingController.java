package com.musicclubapp.controller;

import com.musicclubapp.dto.MeetingResponse;
import com.musicclubapp.dto.MeetingRsvpRequest;
import com.musicclubapp.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Spotkania z czatu: odpowiedz i odwolanie. Zaklada sie je przez rozmowe albo czat klanu. */
@RestController
@RequestMapping("/api/meetings")
@Tag(name = "Spotkania", description = "Spotkania wyslane w rozmowach i na czacie klanu")
public class MeetingController {

    private final MeetingService meetings;

    public MeetingController(MeetingService meetings) {
        this.meetings = meetings;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Spotkanie (strona rozmowy albo czlonek klanu)")
    public ResponseEntity<MeetingResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(meetings.get(id, authentication.getName()));
    }

    @PutMapping("/{id}/rsvp")
    @Operation(summary = "Bede / nie dam rady / cofniecie odpowiedzi (status null)")
    public ResponseEntity<MeetingResponse> respond(@PathVariable Long id, @RequestBody MeetingRsvpRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(meetings.respond(id, authentication.getName(), request.status()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Odwolanie spotkania (tylko zakladajacy)")
    public ResponseEntity<MeetingResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(meetings.cancel(id, authentication.getName()));
    }
}
