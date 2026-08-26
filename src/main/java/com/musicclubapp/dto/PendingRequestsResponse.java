package com.musicclubapp.dto;

import java.util.List;

/**
 * Obie listy zaproszen naraz.
 *
 * <p>Jedno zapytanie zamiast dwoch: ekran "Znajomi" i tak pokazuje obie sekcje
 * jednoczesnie, a przy dwoch osobnych endpointach jedna z nich potrafilaby
 * przez chwile pokazywac stan sprzed akcji.</p>
 *
 * @param incoming zaproszenia do mnie - moge je przyjac albo odrzucic
 * @param outgoing zaproszenia ode mnie - moge je tylko anulowac
 */
public record PendingRequestsResponse(
    List<FriendRequestResponse> incoming,
    List<FriendRequestResponse> outgoing
) {
}
