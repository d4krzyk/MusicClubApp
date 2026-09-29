package com.musicclubapp.dto;

/** Strona zgody na zmiane adresu: czyje konto i na jaki adres - zamaskowany. */
public record EmailChangeInfoResponse(String username, String newEmail) {
}
