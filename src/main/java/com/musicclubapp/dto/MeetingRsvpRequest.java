package com.musicclubapp.dto;

import com.musicclubapp.entity.MeetingStatus;

/** Odpowiedz na spotkanie; {@code null} cofa odpowiedz. */
public record MeetingRsvpRequest(MeetingStatus status) {
}
