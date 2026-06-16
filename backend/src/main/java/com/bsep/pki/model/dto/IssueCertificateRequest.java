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
    @Size(max = 64)
    private String commonName;

    @NotBlank
    @Size(max = 64)
    private String organization;

    @Size(max = 64)
    private String organizationalUnit;

    @NotBlank
    @Size(min = 2, max = 2)
    private String country;

    @Size(max = 64)
    private String state;

    @Size(max = 64)
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

    private Long templateId;

    /** Comma-separated SANs, e.g. "DNS:example.com,DNS:*.example.com,IP:1.2.3.4" */
    @Size(max = 1024)
    @Pattern(regexp = "[a-zA-Z0-9.*:,\\-_ @]*", message = "SAN contains invalid characters")
    private String san;

    /** Comma-separated Key Usage bits, e.g. "digitalSignature,keyEncipherment" */
    @Size(max = 256)
    @Pattern(regexp = "[a-zA-Z,]*", message = "Key Usage contains invalid characters")
    private String keyUsage;

    /** Comma-separated Extended Key Usage OIDs, e.g. "serverAuth,clientAuth" */
    @Size(max = 256)
    @Pattern(regexp = "[a-zA-Z,]*", message = "Extended Key Usage contains invalid characters")
    private String extendedKeyUsage;
}
