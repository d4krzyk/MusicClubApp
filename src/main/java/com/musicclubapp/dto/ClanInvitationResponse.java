package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Zaproszenie do klanu widziane przez zaproszonego. */
public record ClanInvitationResponse(Long id, ClanBadge clan, String inviterUsername, int memberCount,
                                     LocalDateTime createdAt,
                                     /** Zasady klanu - zaproszony zapoznaje sie z nimi przed przyjeciem. */
                                     String rules) {
}
