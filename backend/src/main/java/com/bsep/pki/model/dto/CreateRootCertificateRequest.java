package com.bsep.pki.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateRootCertificateRequest {

    @NotBlank
    private String commonName;

    @NotBlank
    private String organization;

    private String organizationalUnit;

    @Size(min = 2, max = 2)
    private String country;

    private String state;

    private String locality;

    @Email
    private String email;

    @Min(1)
    @Max(3650)
    private int validDays;

    private int keySize = 2048;
}

