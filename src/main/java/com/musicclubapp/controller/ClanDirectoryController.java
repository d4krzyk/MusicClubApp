package com.musicclubapp.controller;

import com.musicclubapp.dto.ClanActivityResponse;
import com.musicclubapp.dto.ClanDirectoryResponse;
import com.musicclubapp.dto.ClanDirectorySort;
import com.musicclubapp.service.ClanActivityService;
import com.musicclubapp.service.ClanDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Przegladarka klanow i ranking aktywnosci. */
@RestController
@RequestMapping("/api/clans")
@Tag(name = "Klany", description = "Przegladarka klanow, prosby o dolaczenie, tytuly, ankiety i ranking")
public class ClanDirectoryController {

    private final ClanDirectoryService directory;
    private final ClanActivityService activity;

    public ClanDirectoryController(ClanDirectoryService directory, ClanActivityService activity) {
        this.directory = directory;
        this.activity = activity;
    }

    @GetMapping("/directory")
    @Operation(summary = "Przegladarka klanow: szukanie, filtr gatunku i miasta, sortowanie")
    public ResponseEntity<ClanDirectoryResponse> directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "false") boolean joinable,
            @RequestParam(defaultValue = "0") int radius,
            @RequestParam(defaultValue = "MATCH") ClanDirectorySort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ClanDirectoryService.DOMYSLNIE) int size,
            Authentication auth) {
        return ResponseEntity.ok(directory.list(auth.getName(), q, genre, city, joinable, radius, sort, page, size));
    }

    @GetMapping("/{id}/activity")
    @Operation(summary = "Ranking aktywnosci w klanie (period=WEEK albo ALL)")
    public ResponseEntity<ClanActivityResponse> activity(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "WEEK") String period,
                                                         Authentication auth) {
        return ResponseEntity.ok(activity.activity(id, auth.getName(), period));
    }
}
