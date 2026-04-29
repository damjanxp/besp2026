package com.bsep.pki.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class SharePasswordRequest {

    @NotNull
    private Long targetUserId;

    @NotBlank
    private String encryptedPassword; // Base64 RSA-OAEP encrypted with target user's public key
}

