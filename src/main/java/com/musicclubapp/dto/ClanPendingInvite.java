package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Zaproszenie, ktore klan wyslal i na ktore nikt jeszcze nie odpowiedzial. */
public record ClanPendingInvite(Long id, String inviteeUsername, String inviterUsername, LocalDateTime createdAt,
                                boolean canCancel) {
}
