package com.bsep.pki.model.dto;

import com.bsep.pki.model.entity.CertificateType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class IssueCertificateRequest {

    @NotNull
    private CertificateType type;

    @NotNull
    private Long issuerCertificateId;

    @NotBlank
    private String commonName;

    @NotBlank
    private String organization;

    private String organizationalUnit;

    @NotBlank
    @Size(min = 2, max = 2)
    private String country;

    private String state;
    private String locality;

    @Email
    private String email;

    @NotNull
    @Min(1)
    @Max(3650)
    private Integer validDays;

    private Integer keySize;

    /** ADMIN only: if set, this user becomes the certificate owner instead of the caller. */
    @Email
    private String ownerEmail;
}
