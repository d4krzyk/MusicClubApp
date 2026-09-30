package com.musicclubapp.controller;

import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.MyReportResponse;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.service.ReportService;
import com.musicclubapp.service.ReportDecisionService;
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

import java.util.Map;

/** Zgloszenia uzytkownikow. */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Zgloszenia", description = "Zglaszanie uzytkownikow i panel administratora")
public class ReportController {

    private static final int MAX_SIZE = 50;

    private final ReportService reportService;
    private final ReportDecisionService decisions;

    /* Dwa serwisy, bo to dwie rozne warstwy. */
    public ReportController(ReportService reportService,
                            ReportDecisionService decisions) {
        this.reportService = reportService;
        this.decisions = decisions;
    }

    /* ------------------------------------------------------------------ */
    /*  Dla kazdego zalogowanego                                           */
    /* ------------------------------------------------------------------ */

    /** Zglasza uzytkownika. */
    @PostMapping("/on/{username}")
    @Operation(summary = "Zglasza uzytkownika do administratora")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Zgloszenie przyjete"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409",
                     description = "Zgloszenie samego siebie, powtorzone zgloszenie "
                         + "albo wyczerpany dzienny limit"),
        @ApiResponse(responseCode = "422", description = "Brak powodu albo opisu")
    })
    public ResponseEntity<ReportResponse> report(
            @PathVariable String username,
            @Valid @RequestBody CreateReportRequest request,
            Authentication authentication) {

        ReportResponse created = reportService.create(
            authentication.getName(), username, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Zglasza klan - jego nazwe, skrot, opis albo obrazy. */
    @PostMapping("/clans/{clanId}")
    @Operation(summary = "Zglasza klan do administratora")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Zgloszenie przyjete"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego klanu"),
        @ApiResponse(responseCode = "409",
                     description = "Zgloszenie wlasnego klanu, powtorzone zgloszenie "
                         + "albo wyczerpany dzienny limit"),
        @ApiResponse(responseCode = "422", description = "Brak powodu albo opisu")
    })
    public ResponseEntity<ReportResponse> reportClan(
            @PathVariable Long clanId,
            @Valid @RequestBody CreateReportRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            reportService.createForClan(authentication.getName(), clanId, request));
    }

    /** Wlasne zgloszenia - login bierzemy z sesji, wiec kazdy widzi tylko swoje. */
    @GetMapping("/mine")
    @Operation(summary = "Moje zgloszenia i to, jak sie skonczyly")
    @ApiResponse(responseCode = "200", description = "Strona wlasnych zgloszen")
    public ResponseEntity<Page<MyReportResponse>> mine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(reportService.mine(
            authentication.getName(),
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    /* ------------------------------------------------------------------ */
    /*  Panel administratora                                               */
    /* ------------------------------------------------------------------ */

    @GetMapping("/admin")
    @Operation(summary = "Lista zgloszen, od najnowszych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona zgloszen"),
        @ApiResponse(responseCode = "403", description = "Wymagane uprawnienia administratora")
    })
    public ResponseEntity<Page<ReportResponse>> list(
            @Parameter(description = "Filtr stanu; pusty = wszystkie")
            @RequestParam(required = false) ReportStatus status,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(reportService.list(
            status,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    /** Jedno zgloszenie razem z dowodami. */
    @GetMapping("/admin/{id}")
    @Operation(summary = "Jedno zgloszenie z migawka dowodow")
    public ResponseEntity<ReportResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.get(id));
    }

    /** Ile czeka na decyzje - liczba przy ikonie panelu. */
    @GetMapping("/admin/open-count")
    @Operation(summary = "Liczba zgloszen czekajacych na decyzje")
    public ResponseEntity<Map<String, Long>> openCount() {
        return ResponseEntity.ok(Map.of("count", reportService.openCount()));
    }

    @PostMapping("/admin/{id}/resolve")
    @Operation(summary = "Zamyka zgloszenie decyzja administratora i wykonuje wybrane dzialanie")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zamkniete"),
        @ApiResponse(responseCode = "409",
            description = "Zgloszenie bylo juz zamkniete albo dzialania nie da sie wykonac"),
        @ApiResponse(responseCode = "422", description = "Brak decyzji albo notatki")
    })
    public ResponseEntity<ReportResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
            decisions.resolve(authentication.getName(), id, request));
    }

    /** Otwiera zamknieta sprawe z powrotem - zeby dalo sie zdecydowac inaczej. */
    @PostMapping("/admin/{id}/reopen")
    @Operation(summary = "Otwiera zamkniete zgloszenie z powrotem")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Otwarte ponownie"),
        @ApiResponse(responseCode = "409", description = "Zgloszenie i tak jest otwarte")
    })
    public ResponseEntity<ReportResponse> reopen(
            @PathVariable Long id, Authentication authentication) {

        return ResponseEntity.ok(
            decisions.reopen(authentication.getName(), id));
    }
}
