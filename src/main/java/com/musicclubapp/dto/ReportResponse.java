package com.musicclubapp.dto;

import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zgloszenie widziane przez administratora.
 *
 * <p><b>Zgloszenia widzi WYLACZNIE administrator</b> - nie ma adresu, pod
 * ktorym zwykly uzytkownik obejrzalby zgloszenia na siebie albo cudze.
 * To celowe: wiedza o tym, kto kogo zglosil, jest najkrotsza droga do
 * odwetu, a przy tej skali serwisu anonimowosc zglaszajacego jest
 * warunkiem tego, zeby ktokolwiek cokolwiek zglosil.</p>
 *
 * @param evidence         migawka tresci; pusta przy zgloszeniu profilu
 * @param priorResolved    ile WCZESNIEJSZYCH zgloszen na te osobe uznano
 *                         za zasadne. Jedno zgloszenie moze byc
 *                         nieporozumieniem, piate to juz wzorzec - i to
 *                         zupelnie inna decyzja
 */
public record ReportResponse(
    Long id,
    String reporterUsername,
    String reportedUsername,
    String reportedAvatarUrl,
    ReportReason reason,
    ReportContext context,
    /** Identyfikator posta albo {@code null}; post moze byc juz skasowany. */
    Long postId,
    String description,
    List<EvidenceLineResponse> evidence,
    ReportStatus status,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt,
    String resolvedBy,
    String resolutionNote,
    long priorResolved
) {
}
