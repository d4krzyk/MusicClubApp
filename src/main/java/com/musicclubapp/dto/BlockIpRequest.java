package com.musicclubapp.dto;

import com.musicclubapp.entity.AccountIp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Prosba o zablokowanie adresu sieciowego. */
public record BlockIpRequest(

    @NotBlank(message = "{validation.ip.address.required}")
    @Size(max = AccountIp.MAX_ADDRESS_LENGTH, message = "{validation.ip.address.size}")
    String address,

    @NotBlank(message = "{validation.ip.reason.required}")
    @Size(max = 500, message = "{validation.ip.reason.size}")
    String reason
) {
}
