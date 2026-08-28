package com.musicclubapp.controller;

import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.service.ReportService;
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

/**
 * Zgloszenia uzytkownikow.
 *
 * <p><b>Dwa poziomy dostepu w jednym kontrolerze i to jest tu wyjatek.</b>
 * Skladanie zgloszenia jest dla kazdego zalogowanego, a wszystko pozostale -
 * wylacznie dla administratora. Zwykle takie rzeczy rozdzielamy na dwa
 * kontrolery ({@code ProfileController} / {@code UserController}), ale tutaj
 * chodzi o ten sam zasob i rozdzielenie go dawaloby dwie klasy o niemal
 * identycznej nazwie.</p>
 *
 * <p>Podzial pilnuje {@code SecurityConfig}: adresy administracyjne siedza
 * pod {@code /api/reports/admin/**}, a skladanie zgloszenia pod
 * {@code /api/reports/on/{login}}. Przedrostki sa rozlaczne, wiec regula
 * bezpieczenstwa jest jednoznaczna i widac ja z samego adresu.</p>
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Zgloszenia", description = "Zglaszanie uzytkownikow i panel administratora")
public class ReportController {

    private static final int MAX_SIZE = 50;

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /* ------------------------------------------------------------------ */
    /*  Dla kazdego zalogowanego                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Zglasza uzytkownika.
     *
     * <p>Kogo - w adresie, kto zglasza - z sesji. Zadnej z tych dwoch rzeczy
     * nie da sie podmienic trescia zapytania.</p>
     */
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

    /**
     * Jedno zgloszenie razem z dowodami.
     *
     * <p>Osobny adres od listy, bo dowody sa doczytywane leniwie: lista
     * dwudziestu zgloszen z pelnymi migawkami rozmow to kilkaset linijek
     * tekstu na jedno wejscie do panelu.</p>
     */
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
    @Operation(summary = "Zamyka zgloszenie decyzja administratora")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zamkniete"),
        @ApiResponse(responseCode = "409", description = "Zgloszenie bylo juz zamkniete"),
        @ApiResponse(responseCode = "422", description = "Brak decyzji albo notatki")
    })
    public ResponseEntity<ReportResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
            reportService.resolve(authentication.getName(), id, request));
    }
}
