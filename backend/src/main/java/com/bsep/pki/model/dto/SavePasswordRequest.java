package com.bsep.pki.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class SavePasswordRequest {

    @NotBlank
    private String siteName;

    @NotBlank
    private String username;

    @NotBlank
    private String encryptedPassword; // Base64 encoded RSA-OAEP encrypted password
}

