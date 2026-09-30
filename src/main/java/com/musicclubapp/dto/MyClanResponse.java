package com.musicclubapp.dto;

import java.util.List;

/** Zakladka "Klan": moj klan (albo null) i zaproszenia, ktore na mnie czekaja. */
public record MyClanResponse(ClanResponse clan, List<ClanInvitationResponse> invitations) {
}
