package com.musicclubapp.dto;

import java.util.List;

/** Obie listy zaproszen naraz. */
public record PendingRequestsResponse(
    List<FriendRequestResponse> incoming,
    List<FriendRequestResponse> outgoing
) {
}
