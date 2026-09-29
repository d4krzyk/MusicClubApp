package com.musicclubapp.dto;

/**
 * Powiadomienia: czy serwer w ogole wysyla push, jego klucz (applicationServerKey
 * dla przegladarki), przypomnienia o wydarzeniach i liczba moich urzadzen.
 */
public record PushSettingsResponse(boolean available, String publicKey, boolean eventReminders, long devices) {
}
