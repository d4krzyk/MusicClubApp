package com.musicclubapp.dto;

/**
 * Funkcje serwera i dane prawne widoczne przed zalogowaniem: czy dziala poczta, wersja
 * regulaminu i polityki prywatnosci oraz administrator danych i dostawcy z konfiguracji serwera.
 */
public record PublicInfoResponse(boolean mailEnabled, String termsVersion, String controller, String contactEmail,
                                 String hosting, String mailProvider) {
}
