package com.musicclubapp.dto;

import com.musicclubapp.entity.AccountIp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Prosba o zablokowanie adresu sieciowego.
 *
 * @param address adres do zablokowania - administrator kopiuje go z listy
 *                powiazanych kont, a nie wpisuje z pamieci
 * @param reason  dlaczego; obowiazkowy - blokada bez powodu jest nie do
 *                odwolania po miesiacu, bo nikt nie bedzie pamietal,
 *                czy wolno ja zdjac
 */
public record BlockIpRequest(

    @NotBlank(message = "{validation.ip.address.required}")
    @Size(max = AccountIp.MAX_ADDRESS_LENGTH, message = "{validation.ip.address.size}")
    String address,

    @NotBlank(message = "{validation.ip.reason.required}")
    @Size(max = 500, message = "{validation.ip.reason.size}")
    String reason
) {
}
